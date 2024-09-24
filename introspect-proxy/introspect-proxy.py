from http.server import BaseHTTPRequestHandler, HTTPServer
import json
import jwt

class JsonRequestHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        auth_header = self.headers.get('Authorization')
        if auth_header and auth_header.startswith('Bearer '):
            try:
                token = auth_header.split(' ')[1]
                payload = jwt.decode(token, options={"verify_signature": False})
                self.send_response(200)
                self.send_header('Content-type', 'application/json')
                self.end_headers()
                self.wfile.write(json.dumps(payload).encode())
            except:
                self.send_response(404)
                self.send_header('Content-type', 'text/plain')
                self.end_headers()
                self.wfile.write(b'Unauthorized')
        else:
            self.send_response(404)
            self.send_header('Content-type', 'text/plain')
            self.end_headers()
            self.wfile.write(b'Unauthorized')

def run_server():
    server_address = ('', 8181)
    httpd = HTTPServer(server_address, JsonRequestHandler)
    print('Server running on port 8181...')
    httpd.serve_forever()

if __name__ == '__main__':
    run_server()
