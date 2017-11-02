# Setting up Loopix

## Dependencies

`python external/loopix/setup.py develop` to install Loopix and its dependencies.

## Client

`python external/loopix/loopix/setup_client.py <port> <host> <name> <provider_info>`

Generates a `secretClient.prv` containing the client's private key material and a `publicClient.bin` containing `name` (Client name), `port` (Listening port), `host` (Client hostname), `pub` (Client public key) and `provider_info` (Provider name)

TODO: Add data to DB

## Provider

`python external/loopix/loopix/setup_provider.py <port> <host> <name>`

Generates a `secretProvider.prv` containing the provider's private key material and a `publicProvider.bin` containing `name` (Provider name), `port` (Listening port), `host` (Provider hostname) and `pub` (Provider public key).

## Mix node

`python external/loopix/loopix/setup_mixnode.py <port> <host> <name> <group>`

Generates a `secreteMixnode.prv` containing the mix node's private key material and a `publicMixnocde.bin` containing `name` (Node name), `port` (Listening port), `host` (Node hostname) and `pub` (Node public key).