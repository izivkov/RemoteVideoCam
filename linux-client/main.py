#!/usr/bin/env python3
"""
RemoteVideoCam Linux Client

Discovers a RemoteVideoCam Android app on the local network via mDNS (NSD),
connects over TCP, performs WebRTC signaling, receives the video stream,
and writes decoded video frames to a v4l2 loopback device (e.g. /dev/video0).
"""

from __future__ import annotations

import argparse
import asyncio
import errno
import fcntl
import json
import logging
import os
import signal
import socket as sock_mod
import struct
from dataclasses import dataclass
from enum import IntEnum
from typing import Any

import av
from aiortc import (
    MediaStreamTrack,
    RTCConfiguration,
    RTCIceCandidate,
    RTCPeerConnection,
    RTCSessionDescription,
)
from aiortc.sdp import candidate_from_sdp, candidate_to_sdp
from zeroconf import ServiceStateChange
from zeroconf.asyncio import AsyncServiceBrowser, AsyncServiceInfo, AsyncZeroconf

logger = logging.getLogger("rvc-client")

# ---------------------------------------------------------------------------
# v4l2 loopback writer – uses ioctl directly, no extra dependency needed
# ---------------------------------------------------------------------------


def v4l2_fourcc(a: str, b: str, c: str, d: str) -> int:
    return ord(a) | (ord(b) << 8) | (ord(c) << 16) | (ord(d) << 24)


class V4L2Field(IntEnum):
    NONE = 1


class V4L2BufType(IntEnum):
    VIDEO_OUTPUT = 2


def _ioctl_number(dir_: int, type_: int, nr: int, size: int) -> int:
    return (dir_ << 30) | (size << 16) | (type_ << 8) | nr


_IOC_WRITE = 1
_IOC_READ = 2

# _IOWR('V', 5, struct v4l2_format)  — sizeof(struct v4l2_format) = 208 on x86_64
VIDIOC_S_FMT = _ioctl_number(_IOC_READ | _IOC_WRITE, ord("V"), 5, 208)


@dataclass
class V4L2LoopbackWriter:
    """Writes raw video frames to a v4l2 loopback device."""

    device: str
    width: int
    height: int
    fd: int = -1
    frame_size: int = 0

    _FMT_STRUCT_SIZE = 208

    def open(self) -> None:
        self.fd = os.open(self.device, os.O_WRONLY | os.O_NONBLOCK)

        # Remove O_NONBLOCK after open so writes block normally
        flags = fcntl.fcntl(self.fd, fcntl.F_GETFL)
        fcntl.fcntl(self.fd, fcntl.F_SETFL, flags & ~os.O_NONBLOCK)

        pix_fmt = v4l2_fourcc("Y", "U", "1", "2")  # YUV420p / I420
        self.frame_size = self.width * self.height * 3 // 2

        buf = bytearray(self._FMT_STRUCT_SIZE)
        struct.pack_into(
            "<I I I I I I I",
            buf,
            0,
            V4L2BufType.VIDEO_OUTPUT,
            self.width,
            self.height,
            pix_fmt,
            V4L2Field.NONE,
            self.width,
            self.frame_size,
        )

        try:
            fcntl.ioctl(self.fd, VIDIOC_S_FMT, bytes(buf))
        except OSError as exc:
            if exc.errno == errno.EINVAL:
                logger.warning(
                    "S_FMT ioctl returned EINVAL – the loopback device may "
                    "already be configured.  Continuing anyway."
                )
            else:
                raise

        logger.info(
            "v4l2 loopback %s opened: %dx%d YUV420p (%d bytes/frame)",
            self.device,
            self.width,
            self.height,
            self.frame_size,
        )

    def write_yuv420p(self, data: bytes | bytearray | memoryview) -> None:
        os.write(self.fd, data)

    def close(self) -> None:
        if self.fd >= 0:
            try:
                os.close(self.fd)
            except OSError:
                pass
            self.fd = -1


def yuv420p_from_video_frame(frame: av.VideoFrame) -> bytes:
    """Convert an av.VideoFrame to tightly-packed YUV420p (I420) bytes."""
    yuv = frame.reformat(format="yuv420p")
    planes: list[bytes] = []
    for p in yuv.planes:
        line_size = p.line_size
        h = p.height
        w = p.width
        plane_bytes = bytes(p)
        if line_size == w:
            planes.append(plane_bytes)
        else:
            packed = bytearray()
            for row in range(h):
                packed.extend(plane_bytes[row * line_size : row * line_size + w])
            planes.append(bytes(packed))
    return b"".join(planes)


# ---------------------------------------------------------------------------
# mDNS / NSD discovery  (fully async)
# ---------------------------------------------------------------------------

SERVICE_TYPE = "_org_avmedia_remotevideocam._tcp.local."


@dataclass
class DiscoveredService:
    name: str
    host: str
    port: int


async def discover_camera(timeout: float = 30.0) -> DiscoveredService:
    """Use mDNS to discover a RemoteVideoCam service on the network."""
    loop = asyncio.get_running_loop()
    result_future: asyncio.Future[DiscoveredService] = loop.create_future()
    azc = AsyncZeroconf()

    def on_change(
        zeroconf: Any,
        service_type: str,
        name: str,
        state_change: ServiceStateChange,
    ) -> None:
        if state_change != ServiceStateChange.Added:
            return
        asyncio.ensure_future(_resolve(zeroconf, service_type, name))

    async def _resolve(zeroconf: Any, service_type: str, name: str) -> None:
        info = AsyncServiceInfo(service_type, name)
        if not await info.async_request(zeroconf, 3000):
            return

        addresses = info.parsed_scoped_addresses()
        if not addresses:
            return

        host = addresses[0]
        port = info.port
        svc_name = info.name
        logger.info("Discovered service: %s @ %s:%d", svc_name, host, port)

        if not result_future.done():
            result_future.set_result(
                DiscoveredService(name=svc_name, host=host, port=port)
            )

    browser = AsyncServiceBrowser(azc.zeroconf, SERVICE_TYPE, handlers=[on_change])

    try:
        return await asyncio.wait_for(asyncio.shield(result_future), timeout=timeout)
    except (asyncio.TimeoutError, TimeoutError):
        logger.error("No RemoteVideoCam service found within %.0fs", timeout)
        raise
    finally:
        await browser.async_cancel()
        await azc.async_close()


# ---------------------------------------------------------------------------
# TCP signaling transport  (line-delimited JSON)
# ---------------------------------------------------------------------------


class SignalingTransport:
    """Line-delimited JSON over a plain TCP socket – mirrors
    LocalConnectionSocketHandler on the Android side."""

    def __init__(
        self, reader: asyncio.StreamReader, writer: asyncio.StreamWriter
    ) -> None:
        self._reader = reader
        self._writer = writer

    async def send(self, obj: dict[str, Any]) -> None:
        line = json.dumps(obj, separators=(",", ":")) + "\n"
        self._writer.write(line.encode())
        await self._writer.drain()

    async def recv(self) -> dict[str, Any]:
        line = await self._reader.readline()
        if not line:
            raise ConnectionError("Signaling socket closed")
        return json.loads(line.decode().strip())

    async def close(self) -> None:
        try:
            self._writer.close()
            await self._writer.wait_closed()
        except Exception:
            pass


# ---------------------------------------------------------------------------
# WebRTC session  (Display role)
# ---------------------------------------------------------------------------


class DisplaySession:
    """Implements the 'Display' side of the RemoteVideoCam WebRTC protocol.

    The phone (Camera) creates an SDP offer, we answer, exchange ICE
    candidates, then receive the video track and feed frames to the
    v4l2 loopback writer.
    """

    def __init__(
        self,
        transport: SignalingTransport,
        writer: V4L2LoopbackWriter,
    ) -> None:
        self.transport = transport
        self.writer = writer
        self.pc = RTCPeerConnection(
            configuration=RTCConfiguration(iceServers=[])
        )
        self._running = True
        self._frame_count = 0

    # -- outgoing helpers (Display → Camera) --

    async def _send_webrtc(self, msg: dict[str, Any]) -> None:
        await self.transport.send({"to_camera_webrtc": msg})

    async def _send_answer(self, sdp: str) -> None:
        await self._send_webrtc({"type": "answer", "sdp": sdp})

    async def _send_candidate(self, candidate: RTCIceCandidate) -> None:
        sdp_str = candidate_to_sdp(candidate)
        await self._send_webrtc(
            {
                "type": "candidate",
                "label": candidate.sdpMLineIndex,
                "id": candidate.sdpMid,
                "candidate": sdp_str,
            }
        )

    # -- incoming dispatch --

    async def _handle_offer(self, payload: dict[str, Any]) -> None:
        sdp = payload["sdp"]
        offer = RTCSessionDescription(sdp=sdp, type="offer")

        await self.pc.setRemoteDescription(offer)
        logger.info("Remote offer set")

        answer = await self.pc.createAnswer()
        await self.pc.setLocalDescription(answer)
        logger.info("Local answer created and set")

        await self._send_answer(self.pc.localDescription.sdp)

    async def _handle_candidate(self, payload: dict[str, Any]) -> None:
        candidate_str = payload["candidate"]
        sdp_mid = payload.get("id", "")
        sdp_m_line_index = payload.get("label", 0)

        # The Android WebRTC library includes the "candidate:" prefix in
        # IceCandidate.sdp, but aiortc's candidate_from_sdp() also prepends
        # "candidate:" to the foundation field.  Strip it to avoid ending up
        # with "candidate:candidate:…".
        if candidate_str.startswith("candidate:"):
            candidate_str = candidate_str[len("candidate:") :]

        candidate = candidate_from_sdp(candidate_str)
        candidate.sdpMid = sdp_mid
        candidate.sdpMLineIndex = sdp_m_line_index
        await self.pc.addIceCandidate(candidate)
        logger.debug("Added remote ICE candidate: %s", candidate_str[:80])

    async def _handle_status(self, status: dict[str, Any]) -> None:
        logger.debug("Camera status: %s", status)

    # -- video track sink --

    async def _consume_track(self, track: MediaStreamTrack) -> None:
        logger.info("Consuming video track: kind=%s", track.kind)
        while self._running:
            try:
                frame: av.VideoFrame = await track.recv()
            except Exception:
                logger.info("Video track ended")
                break

            # Lazy-open the loopback writer on first frame so we know the
            # actual resolution coming from the phone.
            if self.writer.fd < 0:
                self.writer.width = frame.width
                self.writer.height = frame.height
                self.writer.open()

            yuv = yuv420p_from_video_frame(frame)
            try:
                self.writer.write_yuv420p(yuv)
            except OSError as exc:
                if exc.errno in (errno.EAGAIN, errno.EPIPE):
                    pass
                else:
                    raise

            self._frame_count += 1
            if self._frame_count == 1:
                logger.info(
                    "First frame written (%dx%d)", frame.width, frame.height
                )
            elif self._frame_count % 300 == 0:
                logger.info("Frames written to loopback: %d", self._frame_count)

    # -- main loop --

    async def run(self) -> None:
        @self.pc.on("track")
        async def on_track(track: MediaStreamTrack) -> None:
            logger.info("Track received: kind=%s", track.kind)
            if track.kind == "video":
                asyncio.ensure_future(self._consume_track(track))
            elif track.kind == "audio":
                logger.info("Audio track received (ignored – no audio output)")

        @self.pc.on("icecandidate")
        async def on_ice(candidate: RTCIceCandidate | None) -> None:
            if candidate is not None:
                await self._send_candidate(candidate)

        @self.pc.on("connectionstatechange")
        async def on_state() -> None:
            state = self.pc.connectionState
            logger.info("WebRTC connection state: %s", state)
            if state in ("failed", "closed"):
                self._running = False

        logger.info("Waiting for signaling messages …")

        while self._running:
            try:
                msg = await self.transport.recv()
            except (ConnectionError, asyncio.IncompleteReadError):
                logger.info("Signaling connection lost")
                break

            if "to_display_webrtc" in msg:
                payload = msg["to_display_webrtc"]
                msg_type = payload.get("type", "")
                logger.info("Signaling ← %s", msg_type)

                if msg_type == "offer":
                    await self._handle_offer(payload)
                elif msg_type == "candidate":
                    await self._handle_candidate(payload)
                elif msg_type == "bye":
                    logger.info("Received bye – stopping")
                    break
                elif msg_type == "answer":
                    logger.warning("Unexpected answer in display role")

            elif "status" in msg:
                await self._handle_status(msg["status"])
            else:
                logger.debug("Unhandled message: %s", json.dumps(msg)[:120])

        await self.stop()

    async def stop(self) -> None:
        self._running = False
        await self.pc.close()
        self.writer.close()
        await self.transport.close()
        logger.info("Session stopped (total frames: %d)", self._frame_count)


# ---------------------------------------------------------------------------
# Network helpers
# ---------------------------------------------------------------------------


def _get_local_ip() -> str:
    """Get the local IP address used for LAN communication."""
    s = sock_mod.socket(sock_mod.AF_INET, sock_mod.SOCK_DGRAM)
    try:
        s.connect(("10.255.255.255", 1))
        return s.getsockname()[0]
    except Exception:
        return "127.0.0.1"
    finally:
        s.close()


# ---------------------------------------------------------------------------
# Connection strategies
# ---------------------------------------------------------------------------


async def connect_direct(host: str, port: int, timeout: float) -> SignalingTransport:
    """Open a TCP connection directly to a host:port."""
    reader, writer = await asyncio.wait_for(
        asyncio.open_connection(host, port), timeout=timeout
    )
    logger.info("Connected to %s:%d", host, port)
    return SignalingTransport(reader, writer)


async def run_as_display_server(
    port: int,
    v4l2_device: str,
    timeout: float,
) -> None:
    """Register our own NSD service and wait for the Camera to connect,
    then run the WebRTC display session."""

    local_ip = _get_local_ip()
    service_name = f"REMOTE_VIDEO_CAM-LinuxClient-{local_ip}"

    azc = AsyncZeroconf()

    from zeroconf import ServiceInfo

    info = ServiceInfo(
        SERVICE_TYPE,
        f"{service_name}.{SERVICE_TYPE}",
        addresses=[sock_mod.inet_aton(local_ip)],
        port=port,
    )
    await azc.async_register_service(info, strict=False)
    logger.info("Registered NSD service '%s' on port %d", service_name, port)

    connected_future: asyncio.Future[
        tuple[asyncio.StreamReader, asyncio.StreamWriter]
    ] = asyncio.get_running_loop().create_future()

    async def on_client(
        reader: asyncio.StreamReader, writer: asyncio.StreamWriter
    ) -> None:
        peer = writer.get_extra_info("peername")
        logger.info("Camera connected from %s", peer)
        if not connected_future.done():
            connected_future.set_result((reader, writer))
        else:
            # Only accept one connection
            writer.close()

    server = await asyncio.start_server(on_client, "0.0.0.0", port)
    addrs = [str(s.getsockname()) for s in server.sockets]
    logger.info("Listening on %s for Camera connection …", ", ".join(addrs))

    try:
        reader, writer = await asyncio.wait_for(connected_future, timeout=timeout)
    except (asyncio.TimeoutError, TimeoutError):
        logger.error("No Camera connected within %.0fs", timeout)
        server.close()
        await server.wait_closed()
        await azc.async_unregister_service(info)
        await azc.async_close()
        raise SystemExit(1)

    transport = SignalingTransport(reader, writer)
    v4l2_writer = V4L2LoopbackWriter(device=v4l2_device, width=640, height=480)
    session = DisplaySession(transport, v4l2_writer)

    try:
        await session.run()
    finally:
        server.close()
        await server.wait_closed()
        await azc.async_unregister_service(info)
        await azc.async_close()


async def run_direct_connection(
    host: str,
    port: int,
    v4l2_device: str,
    timeout: float,
) -> None:
    """Connect directly to a known host:port (bypassing NSD discovery)."""
    transport = await connect_direct(host, port, timeout)
    v4l2_writer = V4L2LoopbackWriter(device=v4l2_device, width=640, height=480)
    session = DisplaySession(transport, v4l2_writer)
    await session.run()


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------


async def async_main(args: argparse.Namespace) -> None:
    v4l2_device: str = args.device
    port: int = args.port
    timeout: float = args.timeout

    # Check the loopback device exists and is writable
    if not os.path.exists(v4l2_device):
        logger.error("v4l2 loopback device %s does not exist", v4l2_device)
        logger.error(
            "Make sure v4l2loopback is loaded: sudo modprobe v4l2loopback"
        )
        raise SystemExit(1)

    if not os.access(v4l2_device, os.W_OK):
        logger.error(
            "No write access to %s – try adding your user to the 'video' "
            "group or run with sudo",
            v4l2_device,
        )
        raise SystemExit(1)

    # Mode 1: Direct connection to a known host
    if args.connect:
        parts = args.connect.rsplit(":", 1)
        if len(parts) != 2:
            logger.error("--connect expects host:port (e.g. 192.168.1.42:19400)")
            raise SystemExit(1)
        host, cport = parts[0], int(parts[1])
        await run_direct_connection(host, cport, v4l2_device, timeout)
        return

    # Mode 2: Listen-only (skip discovery, register NSD, wait)
    if args.listen:
        await run_as_display_server(port, v4l2_device, timeout)
        return

    # Mode 3: Auto – discover, then try connecting directly to what we found.
    # The Android app registers its NSD service and also listens on that port.
    # When we connect, the Camera side sends us the WebRTC offer directly.
    logger.info("Discovering RemoteVideoCam on the network …")
    try:
        service = await discover_camera(timeout=timeout)
        logger.info(
            "Found camera: %s @ %s:%d", service.name, service.host, service.port
        )
    except (asyncio.TimeoutError, TimeoutError):
        logger.info("Discovery timed out – falling back to listen mode")
        await run_as_display_server(port, v4l2_device, timeout)
        return

    # Try connecting directly to the discovered service.  The Android Camera
    # accepts incoming TCP connections and immediately sends a WebRTC offer.
    try:
        await run_direct_connection(
            service.host, service.port, v4l2_device, timeout
        )
    except (ConnectionRefusedError, asyncio.TimeoutError, TimeoutError, OSError) as exc:
        logger.warning(
            "Direct connection to %s:%d failed (%s) – "
            "falling back to listen mode",
            service.host,
            service.port,
            exc,
        )
        await run_as_display_server(port, v4l2_device, timeout)


def main() -> None:
    parser = argparse.ArgumentParser(
        description=(
            "RemoteVideoCam Linux Client – receive video from the Android "
            "app and output to a v4l2 loopback device"
        ),
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""\
examples:
  %(prog)s                                  # auto-discover & listen on default port
  %(prog)s --device /dev/video2             # use a different loopback device
  %(prog)s --listen                         # skip discovery, just listen
  %(prog)s --connect 192.168.1.42:19400     # connect directly to the phone
  %(prog)s --port 19400 --timeout 120
""",
    )
    parser.add_argument(
        "-d",
        "--device",
        default="/dev/video0",
        help="v4l2 loopback device (default: /dev/video0)",
    )
    parser.add_argument(
        "-p",
        "--port",
        type=int,
        default=19400,
        help="TCP port to listen on for Camera connections (default: 19400)",
    )
    parser.add_argument(
        "-t",
        "--timeout",
        type=float,
        default=60.0,
        help="Discovery / connection timeout in seconds (default: 60)",
    )
    parser.add_argument(
        "-l",
        "--listen",
        action="store_true",
        help="Skip mDNS discovery; just register the service and listen",
    )
    parser.add_argument(
        "-c",
        "--connect",
        metavar="HOST:PORT",
        default=None,
        help=(
            "Connect directly to a Camera at HOST:PORT instead of "
            "listening (useful when the phone's IP is known)"
        ),
    )
    parser.add_argument(
        "-v",
        "--verbose",
        action="store_true",
        help="Enable debug logging",
    )
    args = parser.parse_args()

    logging.basicConfig(
        level=logging.DEBUG if args.verbose else logging.INFO,
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
        datefmt="%H:%M:%S",
    )

    if not args.verbose:
        logging.getLogger("aioice").setLevel(logging.WARNING)
        logging.getLogger("aiortc").setLevel(logging.WARNING)
        logging.getLogger("zeroconf").setLevel(logging.WARNING)

    loop = asyncio.new_event_loop()

    def _shutdown() -> None:
        for task in asyncio.all_tasks(loop):
            task.cancel()

    loop.add_signal_handler(signal.SIGINT, _shutdown)
    loop.add_signal_handler(signal.SIGTERM, _shutdown)

    try:
        loop.run_until_complete(async_main(args))
    except asyncio.CancelledError:
        logger.info("Shutting down …")
    except KeyboardInterrupt:
        logger.info("Interrupted")
    finally:
        # Give pending tasks a chance to clean up
        pending = asyncio.all_tasks(loop)
        if pending:
            for t in pending:
                t.cancel()
            loop.run_until_complete(
                asyncio.gather(*pending, return_exceptions=True)
            )
        loop.run_until_complete(loop.shutdown_asyncgens())
        loop.close()


if __name__ == "__main__":
    main()