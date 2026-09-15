# Privacy / Network behavior

DnsHostsGenerator performs network requests to DNS resolvers explicitly
selected by the user.

- DNS queries (DNS-over-TLS on port 853, with fallback to plain UDP/TCP 53)
  are sent only to the resolver configured for the active DNS preset.
- The check resolver `8.8.8.8` is queried via the `dns.google:853` DoT
  endpoint when comparing results.

When using NextDNS Import, domain names supplied by the user are transmitted
to NextDNS to create rewrite records in a temporary profile.

The application does not proxy the user's web traffic.

The application does not collect analytics or telemetry.
