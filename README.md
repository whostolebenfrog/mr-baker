# Baker - the baking service that cares

## Intro

Baker is a clojure web service that acts as a wrapper around [packer](packer.io) and allows you to create virtual machine images. In particular baker is aimed at creating AMIs or Amazon Machine Images for use in AWS.

Wrapping packer gives us two advantages. Firstly it means that we can generate the templates and perform the bakes (generation of the images) via http calls. Secondly it means that we can control and generate the templates programatically using Clojure code. 

## An overview of how we deploy at MixRadio

Baker forms part of our deployment infrastructure. For more information see this [blog post](http://dev.mixrad.io/blog/2014/10/31/How-we-deploy-at-MixRadio/)

## How to use Baker

At MixRadio we use Baker to generate our deployable artifacts. That is we take a base machine image, deploy our services onto them via RPMs and create a machine image. We then deploy these machine images using autoscaling grounds via our deployment service [Maestro](github.com/mixradio/maestro). We actually generate our service images in two passes. First we create a base image with common components installed (e.g. puppet, common linux tools etc). Secondly we deploy our services onto this base image. Doing this in two parts saves quite a lot of time as installing everything onto the base image takes a few minutes and only needs to be done once a week.

Your actual service templates will be fairly unique to you, as such our actual MixRadio templates aren't listed here. Instead we have two examples of installing an RPM via a packer template. The first uses an amazon-ebs builder which creates a new instance in AWS, and executes commands via SSH to fulfil our template. This is probably the simplest builder. The second uses the chroot-builder which builds the image directly on the box that Baker is running on. This is faster and cheaper but means that Baker must be running in AWS. At MixRadio we use chroot-builders.

## Creating your own templates

As you will need to alter code to create templates a namespace has been provided for this purpose, anything in `baker.builders` is safe for you to edit. Templates are registered against http resources in `baker.builders.routes`. The example templates exist in `baker.builders.bake-example-template`.

## Amazon authentication

There are two options for authing against AWS, using a key and secret pair or via IAM. If you are running packer in AWS then simply create an IAM profile for the instance Baker is running on and don't set the key and secret. Alternatively create a new pair and add these to the config on the project.clj.

## How to run

The simplest way to run is via `lein run`


## More info

Look out for a new blog post on [dev.mixrad.io/blog](http://dev.mixrad.io/blog) in the next few weeks with an example of creating a new template.

## Resources

```
GET /healthcheck

Performs a healthcheck.
200 OK json response
500 Healthcheck failed json response.

GET /ping
200 pong

GET /status
200 OK json response
500 Healthcheck failed json response.

GET /amis
200 Returns a list of the latest base and public amis.

GET /amis/:service-name
200 Returns a list of the 10 latest amis baked for the supplied service name

POST /bake/chroot-example/simple-service/1.0.4/hvm
200 Streaming response of packer output, bakes using an example rpm
```
