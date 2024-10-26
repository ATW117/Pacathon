package com.buaisociety.pacman.entity.behavior;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import java.util.Map;
import com.buaisociety.pacman.maze.Maze;
import com.buaisociety.pacman.maze.Tile;
import com.buaisociety.pacman.maze.TileState;
import com.buaisociety.pacman.sprite.DebugDrawing;
import com.cjcrafter.neat.Client;
import com.buaisociety.pacman.Searcher;
import com.buaisociety.pacman.entity.Direction;
import com.buaisociety.pacman.entity.Entity;
import com.buaisociety.pacman.entity.PacmanEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2d;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.joml.Vector2ic;
import java.util.EnumMap;

public class NeatPacmanBehavior implements Behavior {

    private final @NotNull Client client;
    private @Nullable PacmanEntity pacman;
    private Vector2i lastTilePosition;  // Track the last tile Pac-Man was on
    private int updatesSinceLastTileChange = 0;  // Counter for how long Pac-Man has been on the same tile


    // Score modifiers help us maintain "multiple pools" of points.
    // This is great for training, because we can take away points from
    // specific pools of points instead of subtracting from all.
    private int scoreModifier = 0;

    // new variables!! :)
    private int lastScore = 0;
    private int updatesSinceLastScore = 0;
    // New class variable to store the direction weight (more pellets found, higher weight)
    // New class variable to store the direction weight (more pellets found, higher weight)
    private Map<Direction, Integer> directionWeights = new EnumMap<>(Direction.class);

    // Initialize the direction weights
    public NeatPacmanBehavior(@NotNull Client client) {
        this.client = client;
        for (Direction direction : Direction.values()) {
            directionWeights.put(direction, 0);  // Start all directions with weight 0
        }
    }



    /**
     * Returns the desired direction that the entity should move towards.
     *
     * @param entity the entity to get the direction for
     * @return the desired direction for the entity
     */
    @NotNull
    @Override
    public Direction getDirection(@NotNull Entity entity) {
        if (pacman == null) {
            pacman = (PacmanEntity) entity;
        }

        // SPECIAL TRAINING CONDITIONS
        int newScore = pacman.getMaze().getLevelManager().getScore();
        if (newScore > lastScore) {
            lastScore = newScore;
            updatesSinceLastScore = 0;
        }

        if (updatesSinceLastScore++ > 60 * 15) {
            pacman.kill();
            return Direction.UP;
        }
        // END OF SPECIAL TRAINING CONDITIONS

        // We are going to use these directions a lot for different inputs. Get them all once for clarity and brevity
        Direction forward = pacman.getDirection();
        Direction left = pacman.getDirection().left();
        Direction right = pacman.getDirection().right();
        Direction behind = pacman.getDirection().behind();
        Tile currentTile = pacman.getMaze().getTile(pacman.getTilePosition());
        currentTile.setVisited(true);
        Map<Direction, Searcher.SearchResult> nearestPellets = Searcher.findTileInAllDirections(currentTile, tile -> tile.getState() == TileState.PELLET);

                    // Find the direction with the closest pellet
        Direction bestDirection = null;
        int closestDistance = Integer.MAX_VALUE;

        for (Direction direction : nearestPellets.keySet()) {
            Searcher.SearchResult result = nearestPellets.get(direction);
            if (result != null && result.getDistance() < closestDistance) {
                closestDistance = result.getDistance();
                bestDirection = direction;
            }
        }

        // Move toward the closest pellet
        if (bestDirection != null && pacman.canMove(bestDirection)) {
            return bestDirection;
        }


        // Initialize maxDistance
        int maxDistance = -1;

        // Recalculate maxDistance to normalize the distances
        for (Searcher.SearchResult result : nearestPellets.values()) {
            if (result != null) {
                maxDistance = Math.max(maxDistance, result.getDistance());
            }
        }

        // Make sure maxDistance is valid to avoid division by zero
        if (maxDistance <= 0) {
            maxDistance = 1;
        }

        // Calculate the normalized distances for each direction
        float nearestPelletForward = nearestPellets.get(forward) != null ? 1 - (float) nearestPellets.get(forward).getDistance() / maxDistance : 0;
        float nearestPelletLeft = nearestPellets.get(left) != null ? 1 - (float) nearestPellets.get(left).getDistance() / maxDistance : 0;
        float nearestPelletRight = nearestPellets.get(right) != null ? 1 - (float) nearestPellets.get(right).getDistance() / maxDistance : 0;
        float nearestPelletBehind = nearestPellets.get(behind) != null ? 1 - (float) nearestPellets.get(behind).getDistance() / maxDistance : 0;

        boolean canMoveForward = pacman.canMove(forward);
        boolean canMoveLeft = pacman.canMove(left);
        boolean canMoveRight = pacman.canMove(right);
        boolean canMoveBehind = pacman.canMove(behind);

        boolean wallForward = !pacman.canMove(forward);  // There's a wall if Pac-Man can't move forward
        boolean wallLeft = !pacman.canMove(left);
        boolean wallRight = !pacman.canMove(right);
        boolean wallBehind = !pacman.canMove(behind);

        float[] outputs = client.getCalculator().calculate(new float[]{
            canMoveForward ? 1f : 0f,
            canMoveLeft ? 1f : 0f,
            canMoveRight ? 1f : 0f,
            canMoveBehind ? 1f : 0f,
            nearestPelletForward,
            nearestPelletLeft,
            nearestPelletRight,
            nearestPelletBehind,
            wallForward ? 1f : 0f,   // New input for wall in front
            wallLeft ? 1f : 0f,      // New input for wall to the left
            wallRight ? 1f : 0f,     // New input for wall to the right
            wallBehind ? 1f : 0f     // New input for wall behind
        }).join();

        int index = 0;
        float max = outputs[0];
        for (int i = 1; i < outputs.length; i++) {
            if (outputs[i] > max) {
                max = outputs[i];
                index = i;
            }
        }

        Direction newDirection = switch (index) {
            case 0 -> pacman.getDirection();
            case 1 -> pacman.getDirection().left();
            case 2 -> pacman.getDirection().right();
            case 3 -> pacman.getDirection().behind();
            default -> throw new IllegalStateException("Unexpected value: " + index);
        };

        if (!pacman.canMove(newDirection)) {
            client.setScore(client.getScore() - 10);  // Penalize for trying to move into a wall
        }

        int pelletReward = 0;
        for (Direction direction : nearestPellets.keySet()) {
            Searcher.SearchResult result = nearestPellets.get(direction);
            if (result != null && result.getDistance() <= 5) {  // Close distance pellet bonus
                pelletReward += 5 - result.getDistance();  // Reward based on proximity to pellet
            }
        }

        // Check if Pac-Man moved to a new tile
        if (lastTilePosition == null || !lastTilePosition.equals(pacman.getTilePosition())) {
            // Pac-Man has moved to a new tile, reset the counter
            lastTilePosition = new Vector2i(pacman.getTilePosition());
            updatesSinceLastTileChange = 0;
        } else {
            // Pac-Man is still on the same tile, increment the counter
            updatesSinceLastTileChange++;
        }

        // Penalize if Pac-Man stays too long on the same tile
        int timeOnSameTilePenalty = 0;
        if (updatesSinceLastTileChange > 20) {  // Adjust 20 as needed
            timeOnSameTilePenalty = -5;
        }

        int timePenalty = updatesSinceLastScore / 10; 
        int tileVisitPenalty = currentTile.isVisited() ? -5 : 0;
        client.setScore(pacman.getMaze().getLevelManager().getScore() + scoreModifier + pelletReward + tileVisitPenalty - timePenalty + timeOnSameTilePenalty);
        return newDirection;
    }

    @Override
    public void render(@NotNull SpriteBatch batch) {
        // TODO: You can render debug information here
        
        if (pacman != null) {
            DebugDrawing.outlineTile(batch, pacman.getMaze().getTile(pacman.getTilePosition()), Color.RED);
            DebugDrawing.drawDirection(batch, pacman.getTilePosition().x() * Maze.TILE_SIZE, pacman.getTilePosition().y() * Maze.TILE_SIZE, pacman.getDirection(), Color.RED);
        }
         
    }
}