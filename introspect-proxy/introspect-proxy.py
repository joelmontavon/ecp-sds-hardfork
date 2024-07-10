#!/usr/bin/env python3

from http.server import BaseHTTPRequestHandler, HTTPServer
import os
import logging
import sys


listen_port = int( sys.argv[1] )
response_filename = sys.argv[2]

class IntrospectHTTPRequestHandler(BaseHTTPRequestHandler):
  def do_POST( self ):
    self.send_response( 200 )
    self.send_header('Content-type','application/json')
    self.end_headers()
    with open( response_filename, 'rb' ) as response_file:
        contents = response_file.read()
        self.wfile.write( contents )

with HTTPServer( ('', listen_port), IntrospectHTTPRequestHandler ) as server:
    server.serve_forever()
