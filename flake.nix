{
  description = "A Nix-flake for a minecraft modding dev environment.";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs = { self, nixpkgs }:
    let
      supportedSystems = [ "x86_64-linux" "aarch64-linux" "x86_64-darwin" "aarch64-darwin" ];

      forEachSupportedSystem = f: nixpkgs.lib.genAttrs supportedSystems (system: let
        pkgs = import nixpkgs { inherit system; };
        deps = with pkgs; [
          openjdk
          gradle
          libpulseaudio
          libGL
          glfw
          openal
          stdenv.cc.cc.lib
          libXxf86vm
          libXcursor
          libxrandr
        ];
      in
      f {
        pkgs = pkgs;
        deps = deps;
      });

    in
    {
      devShells = forEachSupportedSystem ({ pkgs, deps }: {
        default = pkgs.mkShell {
          packages = deps;
          buildInputs = deps;
          LD_LIBRARY_PATH = pkgs.lib.makeLibraryPath deps;  # Set up the library path for linking

          # tell Intellij to use jdk and gradle in ./.share since nixos doesn't like dynamically linked executables
          # Settings -> Build, Execution, Deployment -> Build Tools -> Gradle
          shellHook = ''
            export BASE_DIR=$(pwd)
            mkdir -p $BASE_DIR/.share

            if [ -L "$BASE_DIR/.share/java" ]; then
              unlink "$BASE_DIR/.share/java"
            fi
            ln -sf ${pkgs.openjdk}/lib/openjdk $BASE_DIR/.share/java

            if [ -L "$BASE_DIR/.share/gradle" ]; then
              unlink "$BASE_DIR/.share/gradle"
            fi
            ln -sf ${pkgs.gradle}/libexec/gradle $BASE_DIR/.share/gradle
            export GRADLE_HOME="$BASE_DIR/.share/gradle"

            export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:${
              pkgs.lib.makeLibraryPath deps
            };
          '';
        };
      });
    };
}
