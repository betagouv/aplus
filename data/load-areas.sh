#! /usr/bin/env bash

set -x

IFS=";"
while read code name type;
do
  type=${type##*\#};
  curl -H "Content-Type: application/json" -XPOST "http://$1:9200/geo/doc/" -d "{ \"code\" : \"$code\", \"name\" : \"$name\", \"type\" : \"$type\" }"
done

set +x
