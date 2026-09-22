{
  description = "Fabric 1.15.2 SchematicPreview development environment";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs = { self, nixpkgs }:
    let
      systems = [ "x86_64-linux" "aarch64-linux" "x86_64-darwin" "aarch64-darwin" ];
      forSystem = function:
        nixpkgs.lib.genAttrs systems (system: let
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
        in function { inherit pkgs deps; });
    in
    {
      devShells = forSystem ({ pkgs, deps }:
        {
          default = pkgs.mkShell {
            packages = deps;
            buildInputs = deps;
            LD_LIBRARY_PATH = pkgs.lib.makeLibraryPath deps;

            shellHook = ''
              export SP_BASE_DIR=$(pwd)
              mkdir -p "$SP_BASE_DIR/.share"

              if [ -L "$SP_BASE_DIR/.share/java" ]; then unlink "$SP_BASE_DIR/.share/java"; fi
              ln -sf ${pkgs.openjdk}/lib/openjdk "$SP_BASE_DIR/.share/java"

              if [ -L "$SP_BASE_DIR/.share/gradle" ]; then unlink "$SP_BASE_DIR/.share/gradle"; fi
              ln -sf ${pkgs.gradle}/libexec/gradle "$SP_BASE_DIR/.share/gradle"
              export GRADLE_HOME="$SP_BASE_DIR/.share/gradle"
              export LD_LIBRARY_PATH="$LD_LIBRARY_PATH:${pkgs.lib.makeLibraryPath deps}"
            '';
          };
        });
    };
}
