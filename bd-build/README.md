# Bulldozer Build

# Create Project

```
{
	"name" : "Bulldozer",
	"description" : "A combination of IaaC and build tools, heavy construction equipment for software developers.",
	"prefix" : "bd",
	"version" : "0.0.1",
	"protection" : "Public",
	"parent" : "alexandria",
	"dependencies" : ["alexandria"],
	"license" : "Apache2"
}
```

* `name`: The name of the project to be created. This will be used as the repository & directory name, and will appear in the packages and maven group ID.
* `description`: A one sentence description of the project. This will be used in the GitHub repository and maven descriptions.
* `prefix`: A prefix for artifact IDs of the modules in this project, generally two letters.
* `version`: The initial version for this project. This must conform to [semver](https://semver.org/), and is generally `"0.0.1"`
* `protection`: The protection level for this project
  * `"Public"` denotes a project that will be visible to the world.
  * `"Private"` denotes a project that will be visible only to people with permissions at the GitHub organization level.
  * `"Sandbox"` denotes a project this is private, and is generally not built with other projects.
* `parent`: The name of the parent project.  Will usually be `"alexandria"`.
* `dependencies`: An array of the names of any projects on which this one depends. Will often be `["alexandria"]`
* `license`: The license to apply to this project.
  * `"Apache2"` should generally be used for all code
  * `"MIT"` may be more appropriate for websites, typescript libraries and the like

# Release

* Install gpg through cygwin (not gpg2, since the plugin looks for gpg.exe specifically)
* Install git and ensure it's on the path
