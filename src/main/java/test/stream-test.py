import requests, sys, time
url = "http://localhost:8080/gemini/v1/chat/stream"
body = {"messages":[{"role":"user","text":"Tell me a story based in Tirupati"}]}
resp = requests.post(url, json=body, stream=True)
for line in resp.iter_lines():
    if line:
        sys.stdout.buffer.write(line + b"\n")
    else: 
        time.sleep(0.1)
        sys.stdout.flush()
        sys.stdout.buffer.write(b"\n")