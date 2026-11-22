"""Check documented HopToDesk entry points with DNS and a TCP handshake only."""
import concurrent.futures
import ipaddress
import socket
import time


ENDPOINTS = (
    ("signal.hoptodesk.com", 80),
    ("signal.hoptodesk.com", 443),
    ("signal2.hoptodesk.com", 443),
    ("turn.hoptodesk.com", 443),
    ("turn-sg.hoptodesk.com", 443),
    ("turn-kr.hoptodesk.com", 443),
)


def check(endpoint):
    hostname, port = endpoint
    try:
        results = socket.getaddrinfo(hostname, port, type=socket.SOCK_STREAM)
    except OSError as e:
        return f"{hostname}:{port} dns=failed errno={e.errno}"
    families = {}
    for family, kind, proto, _, address in results:
        families.setdefault(family, (kind, proto, address))
    statuses = []
    for family, (kind, proto, address) in families.items():
        addr = ipaddress.ip_address(address[0])
        visibility = "global" if addr.is_global else "local/reserved"
        start = time.monotonic()
        try:
            with socket.socket(family, kind, proto) as sock:
                sock.settimeout(4)
                sock.connect(address)
            outcome = f"TCP-established {round((time.monotonic()-start)*1000)} ms"
        except OSError as e:
            outcome = f"TCP-failed ({type(e).__name__}, errno={e.errno})"
        statuses.append(f"IPv{addr.version}/{visibility}: {outcome}")
    return f"{hostname}:{port} " + " | ".join(statuses)


if __name__ == "__main__":
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        for line in pool.map(check, ENDPOINTS):
            print(line)
