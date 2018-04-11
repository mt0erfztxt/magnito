# magnito

## Usage

Write resource, feed it to `magnito.core/resource->sql` and get SQL string/vector back.

## Demo
In Shell
```
source .env
docker-compose up -d
lein repl
```
, then in REPL
```
(require '[magnito.demo :as demo])
(demo/run)
```

## TODO
* Add tests.
* Make code lean and clean.
* Allow multi-path resource trees.
* Allow to use where in root resource.
* Allow to specify field attributes that must be in result.

## License

Copyright © 2018 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
