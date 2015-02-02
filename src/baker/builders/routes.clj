(ns baker.builders.routes
  (:require [baker
             [lock :as lock]]
            [baker.builders
             [bake-example-template :as example]
             [scheduler :as builders-scheduler]]
            [compojure.core :refer [GET PUT POST DELETE defroutes]]))

(defroutes route-defs

  (POST "/example/:name/:version/:virt-type" [name version dry-run virt-type]
        (lock/lockable-bake #(example/bake-example-ami name version dry-run (keyword virt-type))))

  (POST "/chroot-example/:name/:version/:virt-type" [name version dry-run virt-type]
        (lock/lockable-bake #(example/bake-example-ami-chroot name version dry-run (keyword virt-type)))))
