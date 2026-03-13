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
import shutil
import signal
import socket as sock_mod
import struct
import subprocess
from dataclasses import dataclass, field
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


# Struct layout of v4l2_format on x86_64 (verified with kernel headers):
#   offset 0: __u32 type          (4 bytes)
#   offset 4: 4 bytes padding     (alignment for the union)
#   offset 8: union fmt starts here
#     struct v4l2_pix_format:
#       offset  8: __u32 width
#       offset 12: __u32 height
#       offset 16: __u32 pixelformat
#       offset 20: __u32 field
#       offset 24: __u32 bytesperline
#       offset 28: __u32 sizeimage
#       offset 32: __u32 colorspace
#       ...
# Total sizeof(struct v4l2_format) = 208
_V4L2_FMT_STRUCT_SIZE = 208
_V4L2_FMT_PIX_OFFSET = 8  # offset where fmt.pix begins


@dataclass
class V4L2LoopbackWriter:
    """Writes raw video frames to a v4l2 loopback device."""

    device: str
    width: int
    height: int
    fd: int = -1
    frame_size: int = 0

    def open(self) -> None:
        self.fd = os.open(self.device, os.O_WRONLY | os.O_NONBLOCK)

        # Remove O_NONBLOCK after open so writes block normally
        flags = fcntl.fcntl(self.fd, fcntl.F_GETFL)
        fcntl.fcntl(self.fd, fcntl.F_SETFL, flags & ~os.O_NONBLOCK)

        pix_fmt = v4l2_fourcc("Y", "U", "1", "2")  # YUV420p / I420
        self.frame_size = self.width * self.height * 3 // 2

        buf = bytearray(_V4L2_FMT_STRUCT_SIZE)

        # Pack the type field at offset 0
        struct.pack_into("<I", buf, 0, V4L2BufType.VIDEO_OUTPUT)

        # Pack the v4l2_pix_format fields starting at offset 8 (after 4 bytes
        # of padding that the kernel inserts between __u32 type and the union)
        struct.pack_into(
            "<I I I I I I",
            buf,
            _V4L2_FMT_PIX_OFFSET,
            self.width,
            self.height,
            pix_fmt,
            V4L2Field.NONE,
            self.width,       # bytesperline (Y plane stride = width for I420)
            self.frame_size,  # sizeimage
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
# Firewall helpers (firewalld)
# ---------------------------------------------------------------------------


class FirewallManager:
    """Detects whether firewall rules block mDNS and the TCP listen port.

    On systems running ``firewalld`` the default *public* zone typically
    blocks mDNS (UDP 5353) and our TCP listen port.  Instead of invoking
    ``firewall-cmd`` (which hangs waiting for polkit authentication when
    run as a normal user), this helper reads the firewalld XML config
    files directly and prints clear ``sudo`` commands the user can
    copy-paste if ports are blocked.
    """

    _FIREWALLD_CONF = "/etc/firewalld/firewalld.conf"
    _ETC_ZONES = "/etc/firewalld/zones"
    _LIB_ZONES = "/usr/lib/firewalld/zones"

    def __init__(self, tcp_port: int) -> None:
        self._tcp_port = tcp_port

    # ------------------------------------------------------------------

    @staticmethod
    def _read_default_zone() -> str | None:
        """Read the DefaultZone from firewalld.conf."""
        try:
            with open(FirewallManager._FIREWALLD_CONF) as f:
                for line in f:
                    line = line.strip()
                    if line.startswith("DefaultZone="):
                        return line.split("=", 1)[1].strip()
        except OSError:
            return None
        return None

    @staticmethod
    def _read_zone_xml(zone_name: str) -> str | None:
        """Return the raw XML text of a zone file (/etc overrides /usr/lib)."""
        for base in (FirewallManager._ETC_ZONES, FirewallManager._LIB_ZONES):
            path = os.path.join(base, f"{zone_name}.xml")
            try:
                with open(path) as f:
                    return f.read()
            except OSError:
                continue
        return None

    @staticmethod
    def _zone_has_service(xml: str, service: str) -> bool:
        """Simple check: does the zone XML contain a <service name="..."/> entry?"""
        import re
        return bool(re.search(rf'<service\s+name\s*=\s*"{re.escape(service)}"\s*/?\s*>', xml))

    @staticmethod
    def _zone_has_port(xml: str, port: int, proto: str = "tcp") -> bool:
        """Check whether the zone XML has a matching <port .../> entry."""
        import re
        # Matches <port port="19400" protocol="tcp"/>  (attributes in any order)
        for m in re.finditer(r'<port\s+([^>]+)/?\s*>', xml):
            attrs = m.group(1)
            has_port = re.search(rf'port\s*=\s*"{port}"', attrs)
            has_proto = re.search(rf'protocol\s*=\s*"{re.escape(proto)}"', attrs)
            if has_port and has_proto:
                return True
        return False

    @staticmethod
    def _zone_accepts_all(xml: str) -> bool:
        """Check if the zone target is ACCEPT (allows everything)."""
        import re
        return bool(re.search(r'target\s*=\s*"ACCEPT"', xml))

    # ------------------------------------------------------------------

    def check_and_warn(self) -> None:
        """Print actionable warnings if the firewall blocks required ports."""
        zone_name = self._read_default_zone()
        if zone_name is None:
            logger.debug("firewalld not found or not configured – skipping firewall check")
            return

        xml = self._read_zone_xml(zone_name)
        if xml is None:
            logger.debug("Could not read zone '%s' – skipping firewall check", zone_name)
            return

        # Zones with target=ACCEPT allow everything
        if self._zone_accepts_all(xml):
            logger.debug("Zone '%s' accepts all traffic – no firewall issues", zone_name)
            return

        mdns_ok = self._zone_has_service(xml, "mdns")
        tcp_ok = self._zone_has_port(xml, self._tcp_port, "tcp")

        missing: list[str] = []
        if not mdns_ok:
            missing.append("  sudo firewall-cmd --add-service=mdns")
        if not tcp_ok:
            missing.append(f"  sudo firewall-cmd --add-port={self._tcp_port}/tcp")

        if not missing:
            logger.debug(
                "Firewall zone '%s' already allows mDNS and TCP port %d",
                zone_name,
                self._tcp_port,
            )
            return

        logger.warning(
            "Your firewall (zone '%s') is blocking ports needed for "
            "auto-discovery.\n"
            "The Camera won't be able to find this client or connect to it.\n"
            "Run the following once, then restart rvc-client:\n\n%s\n\n"
            "To make the changes permanent add --permanent to each command.\n"
            "Alternatively, use:  rvc-client --connect <phone-ip>:19400",
            zone_name,
            "\n".join(missing),
        )


# ---------------------------------------------------------------------------
# NSD service registration via avahi-publish-service
# ---------------------------------------------------------------------------


class AvahiServicePublisher:
    """Register an mDNS/DNS-SD service using ``avahi-publish-service``.

    When ``avahi-daemon`` is running it owns the mDNS socket (UDP 5353).
    The Python *zeroconf* library opens its own socket which may not be
    visible on the LAN interface when firewalld is active.  Delegating to
    avahi via its CLI tool avoids the conflict entirely.

    Falls back to the Python *zeroconf* library when avahi-publish-service
    is not installed.
    """

    def __init__(self) -> None:
        self._process: subprocess.Popen[bytes] | None = None
        # Fallback objects when avahi is not available
        self._azc: AsyncZeroconf | None = None
        self._service_info: Any = None

    async def register(
        self, service_name: str, service_type_bare: str, port: int, host_ip: str
    ) -> None:
        """*service_type_bare* must NOT have the ``.local.`` suffix, e.g.
        ``_org_avmedia_remotevideocam._tcp``."""
        avahi_bin = shutil.which("avahi-publish-service")
        if avahi_bin is not None:
            cmd = [
                avahi_bin,
                service_name,
                service_type_bare,
                str(port),
            ]
            logger.debug("Starting: %s", " ".join(cmd))
            self._process = subprocess.Popen(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
            )
            # Give it a moment to register
            await asyncio.sleep(0.5)
            if self._process.poll() is not None:
                stderr = (self._process.stderr.read() or b"").decode(errors="replace")
                logger.warning(
                    "avahi-publish-service exited immediately: %s – "
                    "falling back to python-zeroconf",
                    stderr.strip(),
                )
                self._process = None
                await self._register_zeroconf(service_name, service_type_bare, port, host_ip)
            else:
                logger.info(
                    "Registered NSD service '%s' via avahi-publish-service on port %d",
                    service_name,
                    port,
                )
        else:
            logger.debug("avahi-publish-service not found – using python-zeroconf")
            await self._register_zeroconf(service_name, service_type_bare, port, host_ip)

    async def _register_zeroconf(
        self, service_name: str, service_type_bare: str, port: int, host_ip: str
    ) -> None:
        from zeroconf import ServiceInfo

        stype = service_type_bare + ".local."
        self._azc = AsyncZeroconf()
        self._service_info = ServiceInfo(
            stype,
            f"{service_name}.{stype}",
            addresses=[sock_mod.inet_aton(host_ip)],
            port=port,
        )
        await self._azc.async_register_service(self._service_info, strict=False)
        logger.info(
            "Registered NSD service '%s' via python-zeroconf on port %d",
            service_name,
            port,
        )

    async def unregister(self) -> None:
        if self._process is not None:
            self._process.terminate()
            try:
                self._process.wait(timeout=3)
            except subprocess.TimeoutExpired:
                self._process.kill()
            self._process = None
        if self._azc is not None and self._service_info is not None:
            try:
                await self._azc.async_unregister_service(self._service_info)
                await self._azc.async_close()
            except Exception:
                pass
            self._azc = None
            self._service_info = None


# ---------------------------------------------------------------------------
# mDNS / NSD discovery  (fully async)
# ---------------------------------------------------------------------------

SERVICE_TYPE = "_org_avmedia_remotevideocam._tcp.local."
SERVICE_TYPE_BARE = "_org_avmedia_remotevideocam._tcp"


@dataclass
class DiscoveredService:
    name: str
    host: str
    port: int


async def discover_camera(timeout: float = 30.0) -> DiscoveredService:
    """Use mDNS to discover a RemoteVideoCam Camera service on the network.

    The Camera side (Android) registers an NSD service when in camera mode
    using service type ``_org_avmedia_remotevideocam._tcp.``.  We browse for
    that service type here.

    NOTE: In the standard Android-to-Android flow the *Display* registers a
    service and the *Camera* discovers it.  However, if the phone is already
    advertising (e.g. it was started in camera-only mode), this discovery
    function will find it.  The primary auto-connect path for the Linux
    client is ``run_as_display_server`` which mirrors the Android Display
    role (register + listen).
    """
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
            candidate_str = candidate_str[len("candidate:"):]

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
    firewall: FirewallManager | None = None,
) -> None:
    """Register our own NSD service and wait for the Camera to connect,
    then run the WebRTC display session.

    This mirrors the Android Display role: the Display registers an NSD
    service of type ``_org_avmedia_remotevideocam._tcp.`` and opens a TCP
    server socket.  The Camera discovers this service and connects to it.
    Once connected, the Camera sends a WebRTC offer and the session begins.
    """

    local_ip = _get_local_ip()
    service_name = f"REMOTE_VIDEO_CAM-LinuxClient-{local_ip}"

    if firewall is None:
        firewall = FirewallManager(port)
    firewall.check_and_warn()

    publisher = AvahiServicePublisher()
    await publisher.register(service_name, SERVICE_TYPE_BARE, port, local_ip)

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
        await publisher.unregister()
        raise SystemExit(1)

    transport = SignalingTransport(reader, writer)
    v4l2_writer = V4L2LoopbackWriter(device=v4l2_device, width=640, height=480)
    session = DisplaySession(transport, v4l2_writer)

    try:
        await session.run()
    finally:
        server.close()
        await server.wait_closed()
        await publisher.unregister()


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


async def run_discover_then_connect(
    port: int,
    v4l2_device: str,
    timeout: float,
    discovery_timeout: float = 8.0,
) -> None:
    """Auto-connect strategy that mirrors the Android Display role.

    The Android protocol works as follows:
      - The **Display** registers an NSD service and listens on a TCP port.
      - The **Camera** discovers Display services via NSD, then connects.
      - Once the TCP socket is up the Camera sends a WebRTC offer.

    This function therefore:
      1. Warns if firewalld blocks mDNS / TCP (user must open once).
      2. Registers an NSD service via avahi-publish-service (like the
         Android Display).
      3. Simultaneously browses for an already-advertising Camera service
         (in case the phone advertises its own service when in camera mode).
      4. If a Camera service is discovered first, connects directly to it.
      5. Otherwise waits for the Camera to find *us* and connect.
    """

    local_ip = _get_local_ip()
    service_name = f"REMOTE_VIDEO_CAM-LinuxClient-{local_ip}"

    firewall = FirewallManager(port)
    firewall.check_and_warn()

    publisher = AvahiServicePublisher()
    await publisher.register(service_name, SERVICE_TYPE_BARE, port, local_ip)

    # Also spin up a zeroconf browser for discovery (reading does not
    # require the mDNS port to be writable – outbound queries work fine).
    azc = AsyncZeroconf()

    loop = asyncio.get_running_loop()

    # ------------------------------------------------------------------
    # Path A: Camera connects to us (we act as server, standard flow)
    # ------------------------------------------------------------------
    incoming_future: asyncio.Future[
        tuple[asyncio.StreamReader, asyncio.StreamWriter]
    ] = loop.create_future()

    async def on_client(
        reader: asyncio.StreamReader, writer: asyncio.StreamWriter
    ) -> None:
        peer = writer.get_extra_info("peername")
        logger.info("Camera connected from %s", peer)
        if not incoming_future.done():
            incoming_future.set_result((reader, writer))
        else:
            writer.close()

    server = await asyncio.start_server(on_client, "0.0.0.0", port)
    addrs = [str(s.getsockname()) for s in server.sockets]
    logger.info("Listening on %s for Camera connection …", ", ".join(addrs))

    # ------------------------------------------------------------------
    # Path B: We discover a Camera service and connect to it
    # ------------------------------------------------------------------
    discovered_future: asyncio.Future[DiscoveredService] = loop.create_future()

    def _on_browse_change(
        zeroconf: Any,
        service_type: str,
        name: str,
        state_change: ServiceStateChange,
    ) -> None:
        if state_change != ServiceStateChange.Added:
            return
        asyncio.ensure_future(_on_browse_resolve(zeroconf, service_type, name))

    async def _on_browse_resolve(
        zeroconf: Any, service_type: str, name: str
    ) -> None:
        svc_info = AsyncServiceInfo(service_type, name)
        if not await svc_info.async_request(zeroconf, 3000):
            return
        addresses = svc_info.parsed_scoped_addresses()
        if not addresses:
            return
        host = addresses[0]
        svc_port = svc_info.port
        svc_name = svc_info.name

        # Ignore our own registration
        if service_name in (svc_name or ""):
            logger.debug("Ignoring our own NSD service: %s", svc_name)
            return

        logger.info("Discovered Camera service: %s @ %s:%d", svc_name, host, svc_port)
        if not discovered_future.done():
            discovered_future.set_result(
                DiscoveredService(name=svc_name, host=host, port=svc_port)
            )

    browser = AsyncServiceBrowser(
        azc.zeroconf, SERVICE_TYPE, handlers=[_on_browse_change]
    )

    # ------------------------------------------------------------------
    # Wait for whichever path succeeds first
    # ------------------------------------------------------------------
    transport: SignalingTransport | None = None
    try:
        done, pending = await asyncio.wait(
            [
                asyncio.ensure_future(incoming_future),
                asyncio.ensure_future(discovered_future),
            ],
            timeout=timeout,
            return_when=asyncio.FIRST_COMPLETED,
        )

        if not done:
            logger.error(
                "No Camera connected or discovered within %.0fs", timeout
            )
            raise SystemExit(1)

        for p in pending:
            p.cancel()

        for task in done:
            result = task.result()
            if isinstance(result, tuple):
                # Path A won: Camera connected to us
                reader, writer = result
                transport = SignalingTransport(reader, writer)
                break
            elif isinstance(result, DiscoveredService):
                # Path B won: we discovered a Camera service
                svc = result
                logger.info(
                    "Connecting to discovered Camera at %s:%d …",
                    svc.host,
                    svc.port,
                )
                transport = await connect_direct(svc.host, svc.port, timeout)
                break

        if transport is None:
            logger.error("Failed to establish a connection")
            raise SystemExit(1)

        v4l2_writer = V4L2LoopbackWriter(
            device=v4l2_device, width=640, height=480
        )
        session = DisplaySession(transport, v4l2_writer)
        await session.run()

    finally:
        await browser.async_cancel()
        await azc.async_close()
        server.close()
        await server.wait_closed()
        await publisher.unregister()


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
        fw = FirewallManager(port)
        fw.check_and_warn()
        await run_as_display_server(port, v4l2_device, timeout, firewall=fw)
        return

    # Mode 3 (default): Register our NSD service (Display role) and
    # simultaneously browse for a Camera service.  The Camera discovers
    # *us* and connects; if a Camera service is already advertised we can
    # also connect outward.  This mirrors the Android Display role.
    logger.info(
        "Registering NSD service and waiting for Camera "
        "(also browsing for advertised Camera services) …"
    )
    await run_discover_then_connect(port, v4l2_device, timeout)


def main() -> None:
    parser = argparse.ArgumentParser(
        description=(
            "RemoteVideoCam Linux Client – receive video from the Android "
            "app and output to a v4l2 loopback device"
        ),
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""\
examples:
  %(prog)s                                  # register NSD service & wait for Camera
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