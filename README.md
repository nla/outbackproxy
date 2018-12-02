OutbackProxy
============

OutbackProxy is a HTTP/S proxy which replays resources from a web archive rather than fetching them from the
live web.

It queries a capture index such as [OutbackCDX] or pywb's [CDX Server API]. Once an appropriate
capture is located the WARC or ARC record it points to is served from the local disk or a remote server using
byte range requests.

When a client makes a HTTPS request the proxy will intercept it and generate a self-signed certificate on the fly.
Clients may select a particular snapshot using the [Memento] `Accept-Datetime` header.

[OutbackCDX]: https://github.com/nla/outbackcdx
[CDX Server API]: https://github.com/webrecorder/pywb/wiki/CDX-Server-API
[Memento]: https://tools.ietf.org/html/rfc7089

Configuration
-------------

The following environment variables can be set:

    HOST=0.0.0.0                           # address to listen on
    PORT=3128                              # port to listen on
    CDX_URL=http://localhost:9901/myindex  # URL of the CDX server
    WARC_URL=                              # Base URL or path of your WARC files. Leave blank if absolute in CDX
    CA_CERT=                               # PEM file to read/save CA certificate to
    CA_KEY=                                # PEM file to read/save CA private key to

CA Certificate
--------------

In order to support HTTPS requests OutbackProxy needs to sign a certificate for each hostname. On startup it will
generate a CA certificate used to sign per-host certificates. When the `CA_CERT` and `CA_KEY` environment variables are
set, the certificate and its private key are persisted in a PEM file.

Uou can load the CA certificate file into a client so it's trusted and you don't get any SSL warning/errrors. For
example using curl as a client:

    curl --cacert /tmp/ca.crt --proxy localhost:3128 https://example.org/  

Limitations
-----------

OutbackProxy has no user-interface and is currently only suitable as a backend for automated tooling. You could use
a Memento browser extension to control it but for browser usage I recommend [pywb] instead.

Responding to range requests from clients is not yet implemented.

Building and running
--------------------

Install [Maven] and run:

    mvn package
    java -jar target/outbackproxy-0.1.0-jar-with-dependencies.jar

[Maven]: https://maven.apache.org/

License
-------

Copyright 2018 National Library of Australia and contributors

Licensed under the [Apache License, Version 2.0](LICENSE.txt).

Comparison with similar tools
-----------------------------

[pywb] (Python) has a HTTP/S proxy mode with a much wider feature set including a UI and recording mode. Highly
recommended as an all-in-one replay tool.

[OpenWayback] (Java) has a HTTP proxy mode but does not support HTTPS well.

[SolrWayback] (Java) has a SOCKS proxy mode but it's only used to block live web leakage (it still does link rewriting
in proxy mode).

[pywb]: https://pywb.readthedocs.io/en/latest/manual/configuring.html#http-s-proxy-mode
[OpenWayback]: https://github.com/iipc/openwayback
[SolrWayback]: https://github.com/netarchivesuite/solrwayback
