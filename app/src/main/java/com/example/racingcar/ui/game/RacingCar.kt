package com.example.racingcar.ui.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.dp
import com.example.racingcar.R
import com.example.racingcar.models.MovementInput.Accelerometer
import com.example.racingcar.models.MovementInput.Gestures
import com.example.racingcar.models.SwipeDirection
import com.example.racingcar.ui.game.state.BackgroundState
import com.example.racingcar.ui.game.state.BlockersState
import com.example.racingcar.ui.game.state.CarState
import com.example.racingcar.ui.game.state.GameState
import com.example.racingcar.ui.viewmodel.MainViewModel
import com.example.racingcar.utils.Constants
import com.example.racingcar.utils.Constants.CAR_MOVEMENT_SPRING_ANIMATION_STIFFNESS
import com.example.racingcar.utils.Constants.SWIPE_MIN_OFFSET_FROM_MAX_WIDTH
import com.example.racingcar.utils.Constants.TICKER_ANIMATION_DURATION
import kotlin.math.abs

@Composable
fun RacingGame(
    viewModel: MainViewModel, //todo migrate to value state and expose events
    isDevMode: Boolean,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // resources
    val backgroundImageBitmap = ImageBitmap.imageResource(id = R.drawable.bg_road_night)
    val carImageBitmap = ImageBitmap.imageResource(id = R.drawable.ic_car)
    val blockImageBitmap = ImageBitmap.imageResource(id = R.drawable.ic_block_night)

    // states
    val gameScore by viewModel.gameScore.collectAsState()
    val highscore by viewModel.highscore.collectAsState()
    var gameState by remember {
        mutableStateOf(GameState())
    }

    val backgroundSpeed by remember {
        derivedStateOf {
            (gameScore / Constants.GAME_SCORE_TO_VELOCITY_RATIO) + Constants.INITIAL_VELOCITY
        }
    }
    val backgroundState =
        BackgroundState(
            image = backgroundImageBitmap,
            onGameScoreIncrease = {
                if (gameState.isRunning())
                    viewModel.increaseGameScore()
            }
        )
    val carState = CarState(image = carImageBitmap)

    val blockersState = BlockersState(image = blockImageBitmap)


    // ticker
    val infiniteTransition = rememberInfiniteTransition(label = "infinite")

    val ticker by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = TICKER_ANIMATION_DURATION, easing = LinearEasing)
        ),
        label = "ticker"
    )
    BoxWithConstraints(modifier = modifier) {
        ticker //todo find a better way to put it in here!

        val acceleration by viewModel.acceleration.collectAsState()
        val movementInput by viewModel.movementInput.collectAsState()

        if (movementInput == Accelerometer)
            carState.moveWithAcceleration(acceleration)

        var swipeOffsetX by remember { mutableFloatStateOf(0f) }
        val minSwipeOffset by remember {
            mutableIntStateOf(constraints.maxWidth / SWIPE_MIN_OFFSET_FROM_MAX_WIDTH)
        }

        val carOffsetIndex by animateFloatAsState(
            targetValue = carState.position.fromLeftOffsetIndex(),
            label = "car offset index",
            animationSpec = spring(stiffness = CAR_MOVEMENT_SPRING_ANIMATION_STIFFNESS)
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    when (movementInput) {
                        Gestures -> Modifier.pointerInput(Unit) {
                            detectDragGestures(
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    swipeOffsetX += dragAmount.x
                                },
                                onDragEnd = {
                                    when {
                                        (swipeOffsetX < 0 && abs(swipeOffsetX) > minSwipeOffset) -> SwipeDirection.Left
                                        (swipeOffsetX > 0 && abs(swipeOffsetX) > minSwipeOffset) -> SwipeDirection.Right
                                        else -> null
                                    }?.let { direction ->
                                        if (gameState.isRunning())
                                            carState.moveWithGesture(direction)
                                    }

                                    swipeOffsetX = 0F
                                }
                            )
                        }

                        Accelerometer -> Modifier
                    }
                )
        ) {
            RacingGameCanvas(
                gameState = gameState,
                backgroundState = backgroundState,
                backgroundSpeed = backgroundSpeed,
                blockersState = blockersState,
                viewModel = viewModel,
                carState = carState,
                carOffsetIndex = carOffsetIndex
            )

            Text(
                text = "score: $gameScore",
                modifier = Modifier.align(Alignment.TopCenter)
            )
            Text(
                text = "high score: $highscore",
                modifier = Modifier.align(Alignment.TopStart)
            )

            TopActionButtons(
                onSettingsClick = onSettingsClick,
                onPauseGameState = { gameState = GameState(GameState.Status.PAUSED) },
                onResetGameScore = viewModel::resetGameScore,
                isDevMode = isDevMode
            )

            AnimatedVisibility(
                visible = gameState.isStopped() || gameState.isPaused(),
                modifier = Modifier
                    .align(Alignment.Center)
            ) {
                GameStateIndicator(gameState = gameState)
            }

        }
    }

}

@Composable
private fun GameStateIndicator(gameState: GameState) {
    var gameState1 = gameState
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(16.dp)
    ) {
        Text("Game is ${gameState1.status}", color = Color.Black)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            gameState1 = GameState(GameState.Status.RUNNING)
        }) {
            Text(text = "Start!")
        }
    }
}

@Composable
private fun RacingGameCanvas(
    gameState: GameState,
    backgroundState: BackgroundState,
    backgroundSpeed: Int,
    blockersState: BlockersState,
    viewModel: MainViewModel,
    carState: CarState,
    carOffsetIndex: Float
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawBackground(
            gameState = gameState,
            backgroundState = backgroundState,
            backgroundSpeed = backgroundSpeed
        )

        drawBlockers(
            gameState = gameState,
            blockersState = blockersState,
            backgroundSpeed = backgroundSpeed,
            onDraw = { blockerRects ->
                viewModel.updateBlockerRects(blockerRects)
            }
        )

        drawCar(
            carState = carState,
            carOffsetIndex = carOffsetIndex,
            onDraw = { carRect ->
                viewModel.updateCarRect(carRect)
            }
        )

    }
}

private fun DrawScope.drawBlockers(
    gameState: GameState,
    blockersState: BlockersState,
    backgroundSpeed: Int,
    onDraw: (List<Rect>) -> Unit,
) {
    if (!gameState.isStopped()) {
        if (!gameState.isPaused())
            blockersState.move(velocity = backgroundSpeed)
        val rects = blockersState.draw(drawScope = this)
        onDraw(rects)
    }
}

private fun DrawScope.drawCar(
    carState: CarState,
    carOffsetIndex: Float,
    onDraw: (Rect) -> Unit
) {
    val rect = carState.draw(drawScope = this, offsetIndex = carOffsetIndex)
    onDraw(rect)
}

private fun DrawScope.drawBackground(
    gameState: GameState,
    backgroundState: BackgroundState,
    backgroundSpeed: Int
) {
    if (!gameState.isPaused())
        backgroundState.move(velocity = backgroundSpeed)
    backgroundState.draw(drawScope = this)
}

@Composable
fun BoxScope.TopActionButtons(
    onSettingsClick: () -> Unit,
    onPauseGameState: () -> Unit,
    onResetGameScore: () -> Unit,
    isDevMode: Boolean,
) {
    Column(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = onSettingsClick,
        ) {
            Icon(
                painter = rememberVectorPainter(image = Icons.Filled.Settings),
                contentDescription = "settings"
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "Settings")
        }
        Button(
            onClick = onPauseGameState,
            modifier = Modifier
        ) {
            Icon(
                painter = rememberVectorPainter(image = Icons.Filled.Pause),
                contentDescription = "pause"
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "Pause")
        }
        if (isDevMode) {
            Button(onClick = onResetGameScore) {
                Icon(
                    painter = rememberVectorPainter(image = Icons.Filled.Refresh),
                    contentDescription = "reset"
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Reset")
            }
        }
    }
}
