{
  description = "bloop";

  inputs = {
    nixpkgs.url = github:nixos/nixpkgs/nixpkgs-unstable;
    flake-utils.url = github:numtide/flake-utils;
  };

  outputs = { self, nixpkgs, flake-utils, ... }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs {
          inherit system;
          overlays = [
            (f: p: {
              sbt = p.sbt.override { jre = p.graalvm-ce; };
            })
          ];
        };

        jdk = pkgs.graalvm-ce;

        jvmInputs = with pkgs; [
          scalafmt
          coursier
          jdk
          sbt
          bloop
        ];

        jvmHook = ''
          export JAVA_HOME="${jdk}"
        '';

      in
      {
        devShells.default = pkgs.mkShell {
          name = "bloop-dev-shell";
          buildInputs = jvmInputs; 
          shellHook = jvmHook;
        };
      }
    );

}

