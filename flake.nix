{
  description = "Yass Reloaded Nix package";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-24.11";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachSystem [ "x86_64-linux" "aarch64-linux" ] (system:
      let
        pkgs = import nixpkgs { inherit system; };
        lib = pkgs.lib;
        pname = "yass-reloaded";
        version = "2026.4";
        runtimeLibs = with pkgs; [
          alsa-lib
          fontconfig
          freetype
          glib
          gtk3
          harfbuzz
          libGL
          libX11
          libXcursor
          libXi
          libXrandr
          libXrender
          libXtst
          pango
          zlib
        ];
      in {
        packages.default = pkgs.stdenv.mkDerivation {
          inherit pname version;
          src = ./.;

          nativeBuildInputs = with pkgs; [
            jdk21
            makeWrapper
            maven
          ];

          buildInputs = with pkgs; [
            ffmpeg
            aubio
          ] ++ runtimeLibs;

          buildPhase = ''
            runHook preBuild
            export HOME="$TMPDIR"
            mvn -q clean package -DskipTests -Djavafx.platform=linux
            runHook postBuild
          '';

          installPhase = ''
            runHook preInstall

            mkdir -p "$out/share/${pname}"
            mkdir -p "$out/bin"
            mkdir -p "$out/share/applications"
            mkdir -p "$out/share/icons/hicolor/256x256/apps"

            cp target/Yass-Reloaded.jar "$out/share/${pname}/Yass-Reloaded.jar"
            cp src/yass/resources/icons/yass-icon.png "$out/share/icons/hicolor/256x256/apps/yass-reloaded.png"

            cat > "$out/share/applications/yass-reloaded.desktop" <<EOF
[Desktop Entry]
Version=1.0
Type=Application
Name=Yass Reloaded
Comment=Karaoke editor for UltraStar songs
Exec=$out/bin/yass-reloaded
Icon=yass-reloaded
Terminal=false
Categories=AudioVideo;Audio;Music;Utility;
StartupWMClass=YassMain
EOF

            makeWrapper ${pkgs.jdk21}/bin/java "$out/bin/yass-reloaded" \
              --prefix PATH : ${lib.makeBinPath [ pkgs.ffmpeg pkgs.aubio ]} \
              --prefix LD_LIBRARY_PATH : ${lib.makeLibraryPath runtimeLibs} \
              --add-flags "-jar $out/share/${pname}/Yass-Reloaded.jar"

            runHook postInstall
          '';

          meta = with lib; {
            description = "Yass Reloaded karaoke editor";
            homepage = "https://github.com/DoubleDee73/Yass-Reloaded";
            license = licenses.gpl3Plus;
            mainProgram = "yass-reloaded";
            platforms = platforms.linux;
          };
        };

        apps.default = {
          type = "app";
          program = "${self.packages.${system}.default}/bin/yass-reloaded";
        };

        devShells.default = pkgs.mkShell {
          packages = with pkgs; [
            jdk21
            maven
            ffmpeg
            aubio
          ];
        };
      });
}
