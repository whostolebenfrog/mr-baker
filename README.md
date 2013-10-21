# Ditto - the baking service that cares

## Intro

Ditto bakes amis (amazon machine images) by generating templates for packer and then
invocing the packer command line tool on those json templates.

It starts by taking the base nokia ami and installing good things like ruby and puppet.
It runs puppet which installs more good things and in particular sets up auth using our
fancy LDAP TOTP stuff.

Ditto then bakes this into our entertainment base ami. Before it shuts down it re-enables
the first time scripts that base base nokia ami provide. This means that a new
user can be created on our base ami as if it were the nokia base ami. Thus giving us
the SSH access that packer requires. It also adds this next expected user to the WHEEL
group in the ssh config so that packer can get in without using a TOTP. Finally it makes
sure that puppet isn't set to run automatically as it would revert all those auth changes!

What this means is that you can't just sign in to an entertainemnt base image using LDAP.
Thus we also create a public image which is just the base image with puppet re-enabled 
and auth back to where it should be.

Packer can't use the public image auth and you can't use the entertainment image auth.

Nokia provide a new base image at some point on wednesday so first thing thursday morning
we generate new base and entertainment images.

Finally, and most importantly ditto produces services amis. These take the entertainemt
base ami and yum install the service rpm. They then re-enable puppet and make the ami
available to prod.

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
