package net.cserny.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.*;

public class GameScreen extends ScreenAdapter {

    private static final float MOVE_TIME = 1F;
    private static final float WORLD_WIDTH = 640;
    private static final float WORLD_HEIGHT = 480;
    private static final int SNAKE_MOVEMENT = 32;
    private static final int RIGHT = 0, LEFT = 1, UP = 2, DOWN = 3;
    private static final int GRID_CELL = 32;
    private static final int POINTS_PER_APPLE = 20;
    private static final String GAME_OVER_MSG = "Game Over... tap <space> to restart!";

    private SpriteBatch batch;
    private Viewport viewport;
    private Camera camera;
    private Texture snakeHead;
    private Texture snakeBody;
    private Texture apple;
    private float timer = MOVE_TIME;
    private float snakeX = 0, snakeY = 0;
    private float snakeXBeforeUpdate = 0, snakeYBeforeUpdate = 0;
    private float appleX, appleY;
    private int snakeDirection = RIGHT;
    private int score = 0;
    private boolean appleAvailable = false;
    private boolean directionSet = false;
    private Array<BodyPart> bodyParts = new Array<>();
    private ShapeRenderer shapeRenderer;
    private State state = State.PLAYING;
    private BitmapFont bitmapFont;
    private FrameRate frameRate;

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height);
        frameRate.resize(width, height);
    }

    @Override
    public void show() {
        camera = new OrthographicCamera(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.position.set(WORLD_WIDTH / 2, WORLD_HEIGHT / 2, 0);
        camera.update();
        viewport = new FitViewport(WORLD_WIDTH, WORLD_HEIGHT, camera);
        batch = new SpriteBatch();
        snakeHead = new Texture("snakehead.png");
        snakeBody = new Texture("snakebody.png");
        apple = new Texture("apple.png");
        shapeRenderer = new ShapeRenderer();
        bitmapFont = new BitmapFont();
        frameRate = new FrameRate();
    }

    @Override
    public void render(float delta) {
        switch (state) {
            case PLAYING:
                queryInput();
                updateSnake(delta);
                checkAppleCollision();
                checkAndPlaceApple();
                break;
            case GAME_OVER:
                checkForRestart();
                break;
        }
        clearScreen();
//        drawGrid();
        draw();

        frameRate.update();
        frameRate.render();
    }

    private void addToScore() {
        score += POINTS_PER_APPLE;
    }

    private void updateSnake(float delta) {
        timer -= delta;
        if (timer <= 0) {
            timer = MOVE_TIME;
            moveSnake();
            checkForOutOfBounds();
            checkSnakeBodyCollision();
            updateBodyPartsPosition();
            directionSet = false;
        }
    }

    private void checkSnakeBodyCollision() {
        for (BodyPart part : bodyParts) {
            if (part.x == snakeX && part.y == snakeY) {
                state = State.GAME_OVER;
            }
        }
    }

    private void draw() {
        batch.setProjectionMatrix(camera.projection);
        batch.setTransformMatrix(camera.view);
        batch.begin();
        batch.draw(snakeHead, snakeX, snakeY);
        for (BodyPart bodyPart : bodyParts) {
            bodyPart.draw(batch);
        }
        if (appleAvailable) {
            batch.draw(apple, appleX, appleY);
        }
        if (state == State.GAME_OVER) {
            GlyphLayout gameOverLayout = new GlyphLayout();
            gameOverLayout.setText(bitmapFont, GAME_OVER_MSG);
            bitmapFont.draw(batch, GAME_OVER_MSG,
                    (viewport.getWorldWidth() - gameOverLayout.width) / 2,
                    (viewport.getWorldHeight() - gameOverLayout.height) / 2);
        }
        drawScore();
        batch.end();
    }

    private void drawGrid() {
        shapeRenderer.setProjectionMatrix(camera.projection);
        shapeRenderer.setTransformMatrix(camera.view);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        for (int x = 0; x < viewport.getWorldWidth(); x += SNAKE_MOVEMENT) {
            for (int y = 0; y < viewport.getWorldHeight(); y += SNAKE_MOVEMENT) {
                shapeRenderer.rect(x, y, GRID_CELL, GRID_CELL);
            }
        }
        shapeRenderer.end();
    }

    private void checkForRestart() {
        if (Gdx.input.isKeyPressed(Input.Keys.SPACE)) {
            doRestart();
        }
    }

    private void doRestart() {
        state = State.PLAYING;
        bodyParts.clear();
        snakeDirection = RIGHT;
        directionSet = false;
        timer = MOVE_TIME;
        snakeX = 0;
        snakeY = 0;
        snakeXBeforeUpdate = 0;
        snakeYBeforeUpdate = 0;
        appleAvailable = false;
        score = 0;
    }

    private void updateIfNotOppositeDirection(int newSnakeDirection, int oppositeDirection) {
        if (snakeDirection != oppositeDirection) {
            snakeDirection = newSnakeDirection;
        }
    }

    private void checkAndPlaceApple() {
        if (!appleAvailable) {
            do {
                appleX = MathUtils.random((int) viewport.getWorldWidth() / SNAKE_MOVEMENT - 1) * SNAKE_MOVEMENT;
                appleY = MathUtils.random((int) viewport.getWorldHeight() / SNAKE_MOVEMENT - 1) * SNAKE_MOVEMENT;
                appleAvailable = true;
            } while (applePositionIsNotValid());
        }
    }

    private boolean applePositionIsNotValid() {
        boolean appleIsOnSnakeHead = appleX == snakeX && appleY == snakeY;
        boolean appleIsOnSnakeBody = false;
        for (BodyPart part : bodyParts) {
            if (part.x == appleX && part.y == appleY) {
                appleIsOnSnakeBody = true;
                break;
            }
        }

        return appleIsOnSnakeHead && appleIsOnSnakeBody;
    }

    private void checkAppleCollision() {
        if (appleAvailable && appleX == snakeX && appleY == snakeY) {
            BodyPart bodyPart = new BodyPart(snakeBody);
            bodyPart.updateBodyPosition(snakeX, snakeY);
            bodyParts.insert(0, bodyPart);
            addToScore();
            appleAvailable = false;
        }
    }

    private void drawScore() {
        if (state == State.PLAYING) {
            String scoreAsString = Integer.toString(score);
            GlyphLayout scoreLayout = new GlyphLayout();
            scoreLayout.setText(bitmapFont, scoreAsString);
            bitmapFont.draw(batch, scoreAsString,
                    (viewport.getWorldWidth() - scoreLayout.width) / 2,
                    (4 * viewport.getWorldHeight() / 5) - scoreLayout.height / 2);
        }
    }

    private void clearScreen() {
        Gdx.gl.glClearColor(Color.BLACK.r, Color.BLACK.g, Color.BLACK.b, Color.BLACK.a);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    }

    private void checkForOutOfBounds() {
        if (snakeX >= viewport.getWorldWidth()) {
            snakeX = 0;
        }
        if (snakeX < 0) {
            snakeX = viewport.getWorldWidth() - SNAKE_MOVEMENT;
        }
        if (snakeY >= viewport.getWorldHeight()) {
            snakeY = 0;
        }
        if (snakeY < 0) {
            snakeY = viewport.getWorldHeight() - SNAKE_MOVEMENT;
        }
    }

    private void updateDirection(int newSnakeDirection) {
        if (!directionSet && snakeDirection != newSnakeDirection) {
            directionSet = true;
            switch (newSnakeDirection) {
                case LEFT:
                    updateIfNotOppositeDirection(newSnakeDirection, RIGHT);
                    break;
                case RIGHT:
                    updateIfNotOppositeDirection(newSnakeDirection, LEFT);
                    break;
                case UP:
                    updateIfNotOppositeDirection(newSnakeDirection, DOWN);
                    break;
                case DOWN:
                    updateIfNotOppositeDirection(newSnakeDirection, UP);
                    break;
            }
        }
    }

    private void moveSnake() {
        snakeXBeforeUpdate = snakeX;
        snakeYBeforeUpdate = snakeY;

        switch (snakeDirection) {
            case RIGHT:
                snakeX += SNAKE_MOVEMENT;
                break;
            case LEFT:
                snakeX -= SNAKE_MOVEMENT;
                break;
            case UP:
                snakeY += SNAKE_MOVEMENT;
                break;
            case DOWN:
                snakeY -= SNAKE_MOVEMENT;
                break;
        }
    }

    private void updateBodyPartsPosition() {
        if (bodyParts.size > 0) {
            BodyPart bodyPart = bodyParts.removeIndex(0);
            bodyPart.updateBodyPosition(snakeXBeforeUpdate, snakeYBeforeUpdate);
            bodyParts.add(bodyPart);
        }
    }

    private void queryInput() {
        boolean lPressed = Gdx.input.isKeyPressed(Input.Keys.LEFT);
        boolean rPressed = Gdx.input.isKeyPressed(Input.Keys.RIGHT);
        boolean uPressed = Gdx.input.isKeyPressed(Input.Keys.UP);
        boolean dPressed = Gdx.input.isKeyPressed(Input.Keys.DOWN);

        if (lPressed) {
            updateDirection(LEFT);
        }
        if (rPressed) {
            updateDirection(RIGHT);
        }
        if (uPressed) {
            updateDirection(UP);
        }
        if (dPressed) {
            updateDirection(DOWN);
        }
    }

    private class BodyPart {

        private float x, y;
        private Texture texture;

        public BodyPart(Texture texture) {
            this.texture = texture;
        }

        public void updateBodyPosition(float x, float y) {
            this.x = x;
            this.y = y;
        }

        public void draw(Batch batch) {
            if (!(x == snakeX && y == snakeY)) {
                batch.draw(texture, x, y);
            }
        }
    }
}
