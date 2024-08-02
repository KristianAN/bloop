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
              sbt = p.sbt.override { jre = p.temurin-bin-21; };
            })
          ];
        };

        jdk = pkgs.temurin-bin-21;

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

