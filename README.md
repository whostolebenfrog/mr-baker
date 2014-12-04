# Ditto - the baking service that cares

## Intro

Ditto bakes amis (amazon machine images) by generating templates for packer and then invocing the packer command line tool on those json templates.

It starts by taking the base mixradio ami and installing good things like ruby and puppet.  It runs puppet which installs more good things and in particular sets up auth using our fancy LDAP TOTP stuff.

Ditto then bakes this into our base ami.

Finally, and most importantly ditto produces services amis. These take the entertainemt base ami and yum install the service rpm. They then re-enable puppet and make the ami available to prod.

## Resources

GET /healthcheck

Performs a healthcheck.
200 OK json response
500 Healthcheck failed json response.

GET /1.x/ping
200 pong

GET /1.x/status
200 OK json response
500 Healthcheck failed json response.

GET /1.x/amis
200 Returns a list of the latest nokia, base and public amis.

GET /1.x/amis/:service-name
200 Returns a list of the 10 latest amis baked for the supplied service name

POST /1.x/bake/entertainment-ami
200 Bakes a new version of the entertainment base ami based on the latest nokia ami.
    Streams a text response of packers output as it creates the ami.

POST /1.x/bake/public-ami
200 Bakes a new public ami based on the latest base entertainment ami.
    Streams a text response of packers output as it creates the ami.

POST /1.x/:service-name/:service-version
200 Bakes a new ami for the supplied service name and version. Finds the service rpm
    in yum repo. Looks up the rpm iteration automatically.
    Streams a text response of packers output as it creates the ami.

GET /1.x/pokemon
200 Prints an ascii representation of ditto

GET /1.x/icon
200 Returns the icon png for this service
