package com.buaisociety.pacman.sprite;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Array;
import com.buaisociety.pacman.util.Disposable;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector2i;
import org.joml.Vector2ic;

public class GrayscaleSpriteSheet implements Disposable {

    private final @NotNull Vector2i tileSize;
    private final @NotNull Vector2i tiles;
    private final @NotNull Vector2i currentTile;
    private final @NotNull TextureRegion currentTileRegion;

    private final @NotNull ShaderProgram shader;
    private final @NotNull Array<Color> colors;

    public GrayscaleSpriteSheet(@NotNull Texture texture, int tileSize) {
        this(texture, new Vector2i(tileSize, tileSize));
    }

    public GrayscaleSpriteSheet(@NotNull Texture texture, @NotNull Vector2i tileSize) {
        this.tileSize = tileSize;
        this.tiles = new Vector2i(texture.getWidth() / tileSize.x, texture.getHeight() / tileSize.y);
        this.currentTile = new Vector2i();
        this.currentTileRegion = new TextureRegion(texture);

        ShaderProgram.pedantic = false;
        shader = new ShaderProgram(Gdx.files.internal("shaders/grayscale.vert"), Gdx.files.internal("shaders/grayscale.frag"));
        if (!shader.isCompiled()) {
            throw new RuntimeException("Shader compile error: " + shader.getLog());
        }

        colors = new Array<>(new Color[] {
            Color.CLEAR, Color.PINK
        });
    }

    public @NotNull Vector2ic getTileSize() {
        return tileSize;
    }

    public @NotNull Vector2ic getTiles() {
        return tiles;
    }

    public @NotNull Vector2ic getCurrentTile() {
        return currentTile;
    }

    public void setCurrentTile(int x, int y) {
        if (x < 0 || x >= tiles.x || y < 0 || y >= tiles.y) {
            throw new IllegalArgumentException("Invalid tile coordinates: " + x + ", " + y);
        }

        currentTile.set(x, y);
        currentTileRegion.setRegion(x * tileSize.x, y * tileSize.y, tileSize.x, tileSize.y);
    }

    public @NotNull Array<Color> getColors() {
        return colors;
    }

    public void setColors(@NotNull Color... colors) {
        this.colors.clear();
        this.colors.addAll(colors);
    }

    public void render(@NotNull SpriteBatch batch, int x, int y) {
        batch.setShader(shader);
        shader.bind();
        for (int i = 0; i < colors.size; i++) {
            shader.setUniformf("u_colors[" + i + "]", colors.get(i));
        }
        shader.setUniformi("u_numColors", colors.size);

        batch.draw(currentTileRegion, x, y);
        batch.setShader(null);
    }

    @Override
    public void dispose() {
        shader.dispose();
        currentTileRegion.getTexture().dispose();
    }
}