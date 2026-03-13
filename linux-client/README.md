# RemoteVideoCam Linux Client

A CLI tool that receives video from the [RemoteVideoCam](https://github.com/nicholasgasior/RemoteVideoCam) Android app over WebRTC and outputs it to a v4l2 loopback device. This lets you use your Android phone's camera as a webcam on Linux — in any application that reads from a V4L2 device (Zoom, OBS, Firefox, Chrome, mpv, etc.).

## Prerequisites

- **Python 3.13+**
- **[uv](https://docs.astral.sh/uv/)** for dependency management
- **v4l2loopback** kernel module

### Installing v4l2loopback

```sh
# Arch / Manjaro
sudo pacman -S v4l2loopback-dkms

# Debian / Ubuntu
sudo apt install v4l2loopback-dkms

# Fedora
sudo dnf install v4l2loopback
```

Load the module:

```sh
sudo modprobe v4l2loopback video_nr=0 card_label="RemoteVideoCam" exclusive_caps=1
```

> **Tip:** Add `exclusive_caps=1` so that browsers and video-call apps correctly detect the device as a capture source.

Verify the device exists:

```sh
v4l2-ctl --device=/dev/video0 --all
```

### Permissions

Your user needs write access to the loopback device. The simplest way is to add yourself to the `video` group:

```sh
sudo usermod -aG video $USER
# Log out and back in for the change to take effect
```

### Firewall (firewalld)

If your Linux distribution uses `firewalld` (Fedora, RHEL, Arch, etc.) and your Wi-Fi interface is in the **public** zone (the default), mDNS traffic and the client's TCP listen port are blocked. Auto-discovery will not work until you open them:

```sh
# Allow mDNS so the phone can discover the Linux client
sudo firewall-cmd --add-service=mdns

# Allow the TCP port so the phone can connect to us
sudo firewall-cmd --add-port=19400/tcp
```

To make the changes survive a reboot, add `--permanent`:

```sh
sudo firewall-cmd --permanent --add-service=mdns
sudo firewall-cmd --permanent --add-port=19400/tcp
```

> **Note:** The client detects a blocking firewall at startup and prints the exact commands you need. If you only use `--connect` mode (direct IP), no firewall changes are required.

## Setup

```sh
cd linux-client
uv sync
```

This creates a virtual environment and installs all dependencies (`aiortc`, `zeroconf`, `av`).

## Usage

Make sure the RemoteVideoCam app is running on your Android phone **in Camera mode** and that both devices are on the same local network.

### Auto-discover and connect

```sh
uv run rvc-client
```

The client will:

1. Register an mDNS (NSD) service on the local network, just like the Android Display app does.
2. Open a TCP server socket and wait for the Camera to discover us and connect.
3. Simultaneously browse for an already-advertising Camera service — if one is found first, connect to it directly.
4. Complete WebRTC signaling (offer/answer + ICE).
5. Decode incoming video frames and write them to `/dev/video0`.

> **How it works:** The Android Camera app discovers Display services via NSD and connects *to them*. By registering our own service, the Camera finds us automatically — no manual IP entry needed.

### Connect directly (known IP)

If you already know the phone's IP address:

```sh
uv run rvc-client --connect 192.168.1.42:19400
```

### Listen mode

Register an NSD service and *only* wait for the Camera to find and connect to you (no outbound discovery):

```sh
uv run rvc-client --listen
```

### All options

```
usage: rvc-client [-h] [-d DEVICE] [-p PORT] [-t TIMEOUT] [-l] [-c HOST:PORT] [-v]

options:
  -d, --device DEVICE       v4l2 loopback device (default: /dev/video0)
  -p, --port PORT           TCP port for listen mode (default: 19400)
  -t, --timeout TIMEOUT     Discovery / connection timeout in seconds (default: 60)
  -l, --listen              Skip discovery; register NSD service and wait
  -c, --connect HOST:PORT   Connect directly to a Camera at HOST:PORT
  -v, --verbose             Enable debug logging (shows WebRTC/ICE details)
```

### Viewing the output

Once frames are being written, open the loopback device with any V4L2 consumer:

```sh
# mpv
mpv av://v4l2:/dev/video0

# ffplay
ffplay /dev/video0

# VLC
vlc v4l2:///dev/video0

# OBS: Add a "Video Capture Device (V4L2)" source pointing to /dev/video0
```

## How it works

```
┌──────────────┐    TCP (port 19400)    ┌──────────────────┐
│  Android App │◄──────────────────────►│   Linux Client   │
│ (Camera mode)│   line-delimited JSON  │  (Display role)  │
└──────┬───────┘                        └────────┬─────────┘
       │                                         │
       │  WebRTC (UDP, DTLS-SRTP)                │
       │  VP8/H.264 video + Opus audio           │
       └─────────────────────────────────────────►│
                                                  │ decode (libav)
                                                  ▼
                                          ┌──────────────┐
                                          │ /dev/video0  │
                                          │ (v4l2loopback)│
                                          └──────────────┘
```

1. **Signaling:** The client registers an NSD service (like the Android Display) and listens on a TCP port. The Camera discovers this service via mDNS, connects over TCP, and sends a WebRTC SDP offer. The client responds with an SDP answer. ICE candidates are exchanged over the same TCP channel. (When using `--connect`, the client connects directly to the phone instead.)

2. **Media:** Once ICE connectivity is established and DTLS completes, the phone streams video (VP8 or H.264) and audio (Opus) over SRTP. The client decodes video frames using `aiortc` / `libav`.

3. **Output:** Decoded frames are converted to YUV420p (I420) and written to the v4l2 loopback device via direct `ioctl` + `write()`.

## Troubleshooting

### "No Camera connected or discovered"

- Make sure the Android app is open and set to **Camera** mode.
- Verify both devices are on the same Wi-Fi network.
- The Camera needs to discover the Linux client's NSD service — make sure your firewall isn't blocking mDNS (UDP port 5353) or the client's TCP port (default 19400).
- Try `--connect <phone-ip>:19400` to bypass NSD discovery entirely.
- Use `--verbose` to see mDNS registration and discovery details.

### "No write access to /dev/video0"

```sh
sudo usermod -aG video $USER
# Then log out and back in
```

Or run with `sudo` as a quick test.

### "S_FMT ioctl returned EINVAL"

This is a warning, not an error. The v4l2loopback device may already have a format configured (e.g., from a previous session). The client will continue and frames will still be written successfully. If you want a clean state:

```sh
sudo modprobe -r v4l2loopback
sudo modprobe v4l2loopback video_nr=0 card_label="RemoteVideoCam" exclusive_caps=1
```

### Video is rotated (portrait instead of landscape)

The phone sends video in its native orientation. The resolution adapts automatically to whatever the phone sends (e.g., 360×640 in portrait). If your consuming application doesn't handle non-standard aspect ratios, rotate the phone to landscape before starting the stream.

### Connection state "failed"

- Check that UDP traffic is not blocked between the two devices (some corporate/guest Wi-Fi networks block peer-to-peer UDP).
- Try `--verbose` to see detailed ICE candidate negotiation logs.
- Ensure no other instance of the client is already connected to the phone.