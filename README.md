# Baker - the baking service that cares

## Intro

*******
TODO: This needs a big rewrite, almost nothing in here makes much sense any more.

Can set up example files and routes, can talk about those in here

Need to talk about:
- justification for existence over just using packer
- assume role and accounts
- keys and iam
- packer
- setting up roles and templates
- keeping up-to-date with master
- builders namespace being where changes are made
- config properties and env
- how to build
- how to run
- ditto optional

*******

Baker bakes amis (amazon machine images) by generating templates for packer and then invocing the packer command line tool on those json templates.

It starts by taking the base mixradio ami and installing good things like ruby and puppet.  It runs puppet which installs more good things and in particular sets up auth using our fancy LDAP TOTP stuff.

Baker then bakes this into our base ami.

Finally, and most importantly baker produces services amis. These take the entertainemt base ami and yum install the service rpm. They then re-enable puppet and make the ami available to prod.

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
200 Returns a list of the latest base and public amis.

GET /1.x/amis/:service-name
200 Returns a list of the 10 latest amis baked for the supplied service name

POST /1.x/bake/entertainment-ami
200 Bakes a new version of the base ami based on the latest amazon linux ami. Streams a text response of packers output as it creates the ami.

POST /1.x/bake/public-ami
200 Bakes a new public ami based on the latest base ami. Streams a text response of packers output as it creates the ami.

POST /1.x/:service-name/:service-version
200 Bakes a new ami for the supplied service name and version. Finds the service rpm in yum repo. Looks up the rpm iteration automatically. Streams a text response of packers output as it creates the ami.
