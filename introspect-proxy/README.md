# introspect-proxy

### *** FOR DEVELOPMENT PURPOSES ONLY ***

_introspect-proxy_ is a stub system that facilitates running the SDS in a development context in which an OAuth2 
introspect endpoint isn't available.

### To run:

Install Docker and run the following commands:

```
docker build -t introspect .
docker compose up
```

Then configure the following property in _application.yaml_:

```
  security:
    oauth2:
      resourceserver:
        opaque-token:
          introspection-uri: http://localhost:8181/introspect
```

This will instantiate a dummy _introspect_ endpoint that will always return the contents of the file 
_my_response_file.json_, the `sub` element of which should contain the fully-qualified FHIR Patient ID
of a test _Patient_ resource that one is working with in a development context.
