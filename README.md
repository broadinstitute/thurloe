# Thurloe

A simple service to store arbitrary key/value pairs

## Installation

### Locally

Install the Java Cryptography Extension (for java 8) as described in this [SO post](https://stackoverflow.com/questions/6481627/java-security-illegal-key-size-or-default-parameters). Tests will fail without it.

Make sure [sbt](http://www.scala-sbt.org/) is installed.  
Make sure you've installed git-secrets.
Then use these commands to ensure git-hooks are run and start a server:

```
$ cp -r hooks/ .git/hooks/ #this step can be skipped if you use the rsync script to spin up locally
$ chmod 755 .git/hooks/apply-get-secrets.sh #this step as well
$ sbt run
```

To create a stand-alone JAR in the `target/scala-2.11` directory:

```
$ sbt assembly
```

### With Docker
Does not require having sbt installed.  To build the JAR and move it to the working directory, run:
```
$ ./docker/build.sh jar
```

To build a docker container from the jar, run:
```
$ ./docker/build.sh -d build
```

Note that you can also run `build.sh` once with both commands.

To run the docker image:
```
$ docker run -p 8000:8000 -d broadinsitute/thurloe:${GIT_SHA:0:12}
```

Then one can issue requests to `localhost`.

> **NOTE:** For Mac OS X users using boot2docker, run `boot2docker ip` to get IP address to issue HTTP requests to

## HTTP API

### POST /api/thurloe

Adds a new key/value pair to Thurloe

```
http --print=hbHB POST http://localhost:8000/api/thurloe < payload.json
curl -X POST -d @payload.json http://localhost:8000/api/thurloe --header "Content-Type: application/json"
```

Where payload.json contains:

```
{
  "userId": "uid",
  "keyValuePair": {
    "key": "k",
    "value": "v"
  }
}
```

Request:

```
POST /api/thurloe HTTP/1.1
Accept: application/json
Accept-Encoding: gzip, deflate
Connection: keep-alive
Content-Length: 80
Content-Type: application/json
Host: localhost:8000
User-Agent: HTTPie/0.9.2

{
    "keyValuePair": {
        "key": "k",
        "value": "v"
    },
    "userId": "uid"
}
```

If the key already exists, a **406 Not Acceptable** will be returned.

### GET /api/thurloe?userId=:userId&key=:key&value=:value

```
http --print=hbHB http://localhost:8000/api/thurloe?userId=u&key=k&value=v
curl http://localhost:8000/api/thurloe?userId=u&key=k&value=v
```

All parameters are optional. 
They act as filters on the returned values in that everything returned must match any and all of the specified parameters.
If any value is specified multiple times (e.g. `userId=aaa&userId=bbb`) then any matching values are returned.

Response:

```
HTTP/1.1 200 OK
Content-Length: 79
Content-Type: application/json; charset=UTF-8
Date: Tue, 15 Sep 2015 18:50:36 GMT
Server: spray-can/1.3.3

[
    {
        "keyValuePair": {
            "key": "k",
            "value": "v"
        },
        "userId": "u"
    }
]
```

### GET /api/thurloe/:user_id/:key

```
http --print=hbHB http://localhost:8000/api/thurloe/uid/k
curl http://localhost:8000/api/thurloe/uid/k
```

Response:

```
HTTP/1.1 200 OK
Content-Length: 79
Content-Type: application/json; charset=UTF-8
Date: Tue, 15 Sep 2015 18:50:36 GMT
Server: spray-can/1.3.3

{
    "keyValuePair": {
        "key": "k",
        "value": "v"
    },
    "userId": "uid"
}
```

If the key or user ID is not found, a **404 Not Found** will be returned.

### GET /api/thurloe/:user_id

```
http --print=hbHB http://localhost:8000/api/thurloe/uid
curl http://localhost:8000/api/thurloe/uid
```

```
HTTP/1.1 200 OK
Content-Length: 124
Content-Type: application/json; charset=UTF-8
Date: Tue, 15 Sep 2015 18:54:13 GMT
Server: spray-can/1.3.3

{
    "keyValuePairs": [
        {
            "key": "k",
            "value": "v"
        },
        {
            "key": "k2",
            "value": "v2"
        }
    ],
    "userId": "uid"
}
```

If the key or user ID is not found, a **404 Not Found** will be returned.

### DELETE /api/thurloe/:user_id/:key

```
http --print=hbHB DELETE http://localhost:8000/api/thurloe/uid/k2
curl -X DELETE http://localhost:8000/api/thurloe/uid/k
```

If the key is not found, a 404 is returned:

```
HTTP/1.1 404 Not Found
Content-Length: 17
Content-Type: text/plain; charset=UTF-8
Date: Tue, 15 Sep 2015 18:58:19 GMT
Server: spray-can/1.3.3

Key not found: k2
```

test
