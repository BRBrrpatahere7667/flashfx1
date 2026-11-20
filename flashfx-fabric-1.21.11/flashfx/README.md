# FlashFX (Fabric 1.21.11, client-side)
Features: jump shockwave ring + dust + spiral + petals, hit bursts, rainbow trail, fullbright, rainbow HUD, halo, wings, hat, petals, stardust.
Open the settings screen with RIGHT SHIFT or /flashfx menu. Settings save to config/flashfx.properties.
Toggles also via: /flashfx jump | hit | trail | bright | hud | halo | wings | hat | petals | stardust | glass

Build:
1. Get the Gradle wrapper: generate a template at https://fabricmc.net/develop (1.21.11) and copy its
   gradlew, gradlew.bat and gradle/ folder into this project, OR run `gradle wrapper` with Gradle 9.x.
2. ./gradlew build  -> jar in build/libs/ (put in .minecraft/mods with Fabric API 0.141.3+1.21.11)
Dev run: ./gradlew runClient

If a method name fails to compile (Yarn names shift between builds), fix that one line; the usual suspects are
World.addParticleClient and DustParticleEffect's constructor.

Glass: "glass" toggle uses a render mixin (translucent layer + alpha tint on all player models; opacity in the menu).
"ghost" is the old no-mixin outline version. If glass does nothing, check latest.log for a skipped
getRenderLayer/getMixColor injector - the Yarn name or signature differs in your build.
