{
  description = "A Nix-flake for a minecraft modding dev environment.";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs =
    { nixpkgs, ... }:
    let
      supportedSystems = [
        "x86_64-linux"
        "aarch64-linux"
        "x86_64-darwin"
        "aarch64-darwin"
      ];

      forEachSupportedSystem =
        f:
        nixpkgs.lib.genAttrs supportedSystems (
          system:
          let
            pkgs = import nixpkgs { inherit system; };
          in
          f pkgs
        );
    in
    {
      devShells = forEachSupportedSystem (
        pkgs:
        let
          lib = pkgs.lib;

          java = pkgs.jetbrains.jdk-no-jcef;

          java8 = pkgs.zulu8;

          runtimeLibs =
            with pkgs;
            [
              libGL
              libpulseaudio
              flite
            ]
            ++ lib.optionals stdenv.hostPlatform.isLinux [
              glfw3-minecraft

              # idk maybe these are needed
              openal
              stdenv.cc.cc.lib
              libXxf86vm
              libXcursor
              libxrandr
            ];
        in
        {
          default = pkgs.mkShell {
            packages = with pkgs; [
              java
              java8
              gradle_9
              git
            ];
            JAVA_HOME = "${java.home}";
            JAVA8_HOME = "${java8.home}";

            LD_LIBRARY_PATH = pkgs.lib.makeLibraryPath runtimeLibs;
          };
        }
      );
    };
}

