(ns ditto.bake-common)

(defn shell [& cmds]
  "Accepts a series of strings to run as shell comands. Runs commands with -x shebang and
   with sudo."
  {:type "shell"
   :execute_command "chmod +x {{ .Path }}; {{ .Vars }} sudo -E sh '{{ .Path }}'"
   :inline_shebang "/bin/sh -x"
   :inline cmds})
