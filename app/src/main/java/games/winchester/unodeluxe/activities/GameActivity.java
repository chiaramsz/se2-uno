package games.winchester.unodeluxe.activities;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.constraint.ConstraintLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import at.laubi.network.Network;
import at.laubi.network.messages.Message;
import at.laubi.network.session.ClientSession;
import butterknife.BindView;
import butterknife.ButterKnife;
import games.winchester.unodeluxe.R;
import games.winchester.unodeluxe.app.Preferences;
import games.winchester.unodeluxe.enums.CardColor;
import games.winchester.unodeluxe.models.Card;
import games.winchester.unodeluxe.dialog.ColorWishDialog;
import games.winchester.unodeluxe.models.Game;
import games.winchester.unodeluxe.models.Player;
import games.winchester.unodeluxe.models.ShakeDetector;
import games.winchester.unodeluxe.utils.NetworkUtils;

import static android.widget.Toast.LENGTH_SHORT;

public class GameActivity extends AppCompatActivity {

    @BindView(R.id.deckView)
    ImageView deckView;

    @BindView(R.id.stack_layout)
    FrameLayout stackLayout;

    @BindView(R.id.handLayout)
    LinearLayout handLayout;

    @BindView(R.id.main_game_layout)
    ConstraintLayout gameLayout;

    @BindView(R.id.ipText)
    TextView ip;

    @BindView(R.id.player2)
    ImageView opponentOne;

    @BindView(R.id.player3)
    ImageView opponentTwo;

    @BindView(R.id.player4)
    ImageView opponentThree;

    private Game game;
    private Network network;
    private Preferences preferences;

    // The following are used for the shake detection
    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private ShakeDetector mShakeDetector;

    private CardColor bgColor = CardColor.RED;

    // Needed to detect swipe event
    private float oldTouchValue = 0f;
    private float newTouchValue = 0f;
    private static final float MIN_DISTANCE = 50f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        ButterKnife.bind(this);

        preferences = Preferences.from(this);

        this.setupNetwork();
        this.setupSensors();
        this.setupGame();
    }

    private void setupGame() {
        String host = getIntent().getStringExtra("host");

        if (host == null) {
            game = new Game(this, new Player("Player1"));
            this.setupMultiplayerHost();
        } else {
            game = new Game(this);
            this.setupMultiplayerClient(host);
        }
    }

    private void setupNetwork() {
        network = new Network();

        network.setFallbackExceptionListener((e, s) -> onFallbackException(e));
        network.setMessageListener((m, s) -> onMessageReceived(m));
        network.setNewSessionListener(this::onNewSession);
        network.setConnectionEndListener(s -> onSessionEnd());
    }

    private void onFallbackException(Exception e) {
        toastUiThread(String.format(getString(R.string.failed_connecting), e.getMessage()));
        Log.e("GameActivity", e.getMessage(), e);
    }

    private void onMessageReceived(Message message) {
        runOnUiThread(() -> game.messageReceived(message));
    }

    private void onNewSession(ClientSession session) {
        runOnUiThread(() -> game.clientConnected(session));
    }

    private void onSessionEnd() {
        runOnUiThread(() -> game.clientDisconnected());
    }

    private void setupSensors() {
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (mSensorManager != null) {
            mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            mShakeDetector = new ShakeDetector();
            mShakeDetector.setOnShakeListener(c -> handleShakeEvent());
        }
    }

    private void setupMultiplayerHost() {
        final String format = getString(R.string.host_message);
        final List<String> networks = NetworkUtils.getLocalIpAddresses();

        if (networks.isEmpty()) return;

        final String hostIp = networks.get(0);

        network.createHost(hostSession -> {
            game.setSession(hostSession);

            runOnUiThread(() -> {
                deckView.setClickable(true);
                deckView.setOnClickListener(l -> game.deckClicked());
            });

            runOnUiThread(() -> ip.setText(String.format(format, hostIp)));
        }, null);

    }

    private void setupMultiplayerClient(String host) {
        network.createClient(host, clientSession -> {
            game.setSession(clientSession);

            runOnUiThread(() -> {
                deckView.setClickable(true);
                deckView.setOnClickListener(l -> game.deckClicked());
            });

        }, (e, s) -> {
            toastUiThread(getString(R.string.connection_failed));
            Log.e("GameActivity", e.getMessage(), e);
            startActivity(new Intent(this, MenuActivity.class));
            finish();
        });
    }

    public static Drawable getImageDrawable(Context c, String imageName) {
        final int resourceIdentifier = c.getResources().getIdentifier(imageName, "drawable", c.getPackageName());

        return c.getResources().getDrawable(resourceIdentifier);
    }

    // used to keep the stack UI up to date with the backend model
    // used to keep the stack UI up to date with the backend model
    public void updateTopCard(String graphic) {
        LayoutInflater inflater = getLayoutInflater();
        View stackLay = inflater.inflate(R.layout.stack, gameLayout, false);
        ImageView card = stackLay.findViewById(R.id.stackView);
        card.setImageDrawable(getImageDrawable(this, graphic));
        Random rand = new Random();
        float randomNum = rand.nextInt(361);
        card.setRotation(randomNum);
        stackLayout.addView(stackLay);

        // prevent stack from looking shit
        if(stackLayout.getChildCount() > 5) {
            stackLayout.removeViews(0, 2);
        }
    }

    public void renderOpponents(List<String> opponents){
        int noOpponents = opponents.size();
        ArrayList<View> views = new ArrayList<>();
        if(noOpponents == 1) {
            opponentTwo.setTag(opponents.get(0));
            views.add(opponentTwo);
        } else if(noOpponents == 2) {
            opponentOne.setTag(opponents.get(0));
            opponentThree.setTag(opponents.get(1));
            views.add(opponentOne);
            views.add(opponentThree);
        } else if(noOpponents == 3) {
            opponentOne.setTag(opponents.get(0));
            opponentTwo.setTag(opponents.get(1));
            opponentThree.setTag(opponents.get(2));
            views.add(opponentOne);
            views.add(opponentTwo);
            views.add(opponentThree);
        }

        for(View w : views) {
            w.setVisibility(View.VISIBLE);
            w.setClickable(true);
            w.setOnClickListener(v -> game.opponentClicked(v.getTag()));
        }
    }



    public void updateColor(CardColor newC){
        CardColor oldC = bgColor;
        TransitionDrawable transition = (TransitionDrawable) gameLayout.getBackground();
        TransitionDrawable transitionRb = (TransitionDrawable) transition.findDrawableByLayerId(R.id.fader_rb);
        TransitionDrawable transitionGy = (TransitionDrawable) transition.findDrawableByLayerId(R.id.fader_gy);

        if(oldC != newC && newC != CardColor.BLACK){
            switch(newC) {
                case RED:
                    if(oldC == CardColor.BLUE) {
                        transitionRb.reverseTransition(700);
                    } else {
                        transitionRb.resetTransition();
                        transition.reverseTransition(700);
                    }
                    break;
                case BLUE:
                    if(oldC == CardColor.RED) {
                        transitionRb.startTransition(700);
                    } else {
                        transitionRb.startTransition(0);
                        transition.reverseTransition(700);
                    }
                    break;
                case GREEN:
                    if(oldC == CardColor.YELLOW) {
                        transitionGy.reverseTransition(700);
                    } else {
                        transitionGy.resetTransition();
                        transition.startTransition(700);
                    }
                    break;
                case YELLOW:
                    if (oldC == CardColor.GREEN) {
                        transitionGy.startTransition(700);
                    } else {
                        transitionGy.startTransition(0);
                        transition.startTransition(700);
                    }
                    break;
                default:
                    break;
            }
            bgColor = newC;
        }
    }

    // used to keep the hand UI up to date with the backend model
    public void addToHand(List<Card> cards) {

        for (Card c : cards) {
            ImageView cardView = new ImageView(GameActivity.this);
            cardView.setPadding(0, 0, 0, 0);
            cardView.setImageDrawable(getImageDrawable(this, c.getGraphic()));
            cardView.setClickable(true);
            cardView.setTag(c);

            cardView.setOnTouchListener((v, event) -> {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        oldTouchValue = event.getY();
                        break;
                    case MotionEvent.ACTION_UP:
                        newTouchValue = event.getY();
                        float deltaY = Math.abs(newTouchValue - oldTouchValue);
                        if (deltaY > MIN_DISTANCE) {
                            // user swiped a card down
                            if(newTouchValue > oldTouchValue) {
                                if(v.getTag() == null) return false;
                                if(game.cheat((Card) v.getTag())) {
                                    handLayout.removeView(v);
                                }
                                return true;
                            }
                        } else if (deltaY <= MIN_DISTANCE) {
                            // user clicked a card
                            if (v.getTag() == null) return false;
                            if (game.cardClicked((Card) v.getTag())) {
                                handLayout.removeView(v);
                                return true;
                            }
                        }
                        break;
                    default:
                        break;
                }

                return false;
            });


            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(125, 195);
            int marginLeft = handLayout.getChildCount() == 0 ? 0 : -30;

            // TODO for bigger displays check density and render accordingly
            // LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(188, 293);
            // int marginLeft = handLayout.getChildCount() == 0 ? 0 : -60;

            layoutParams.setMargins(marginLeft, 0, 0, 0);

            cardView.setLayoutParams(layoutParams);
            handLayout.addView(cardView);
        }

        final int childCount = handLayout.getChildCount();
        View[] children = new View[childCount];
        for (int i = 0; i < childCount; i++) {
            children[i] = handLayout.getChildAt(i);
        }

        Arrays.sort(children, (a, b) -> {
            Card cardA = (Card) a.getTag();
            Card cardB = (Card) b.getTag();

            return cardA.compareTo(cardB);
        });

        handLayout.removeAllViews();

        for (int i = 0; i < childCount; i++) {
            if (i != 0)
                ((LinearLayout.LayoutParams) children[i].getLayoutParams())
                        .setMargins(-30, 0, 0, 0);
            handLayout.addView(children[i]);
        }

    }

    public void wishAColor(Game game) {
        ColorWishDialog cwd = new ColorWishDialog();
        cwd.setCancelable(false);
        cwd.show(getSupportFragmentManager(), "WishColor", game);
    }

    public void notificationNumberOfCardsToDraw(int i) {
        String text = i == 1 ? getString(R.string.draw_one) : "Du musst noch " + i + " Karten ziehen.";

        this.toastUiThread(text, LENGTH_SHORT);
    }

    public void notificationCardNotPlayable() {
        this.toastUiThread(getString(R.string.card_not_possible), LENGTH_SHORT);
    }

    public void notificationGameWon() {
        this.toastUiThread(getString(R.string.round_won), LENGTH_SHORT);
    }

    public void notificationHasPlayableCard() {
        this.toastUiThread(getString(R.string.cards_playable), LENGTH_SHORT);
    }

    public void notificationNotYourTurn() {
        this.toastUiThread(getString(R.string.not_your_turn), LENGTH_SHORT);
    }

    public void notificationYourTurn() {
        this.vibrate();
        this.toastUiThread(getString(R.string.your_turn), LENGTH_SHORT);
    }

    public void notificationCheated() {
        this.toastUiThread(getString(R.string.cheated), LENGTH_SHORT);
    }

    public void notificationAlreadyCheated() {
        this.toastUiThread(getString(R.string.already_cheated), LENGTH_SHORT);
    }

    public void notificationNotAllowedToCheat() {
        this.toastUiThread(getString(R.string.not_allowed_to_cheat), LENGTH_SHORT);
    }

    public void notificationDeckShuffled() {
        this.toastUiThread(getString(R.string.deck_shuffeled), LENGTH_SHORT);
    }

    public void notificationNotAllowedToAccuse() {
        this.toastUiThread(getString(R.string.not_allowed_to_accuse), LENGTH_SHORT);
    }

    public void notificationDrawCardsFirst(int i) {
        this.toastUiThread(String.format(getString(R.string.draw_cards_first), i), LENGTH_SHORT);
    }

    public void notificationCorrectlyAccusedAccuser(String accused) {
        this.toastUiThread(String.format(getString(R.string.correctly_accused_accuser), accused), LENGTH_SHORT);
    }

    public void notificationCorrectlyAccusedAccused(String accuser) {
        this.toastUiThread(String.format(getString(R.string.correctly_accused_accused), accuser), LENGTH_SHORT);
    }

    public void notificationCorrectlyAccusedAll(String accuser, String accused) {
        this.toastUiThread(String.format(getString(R.string.correctly_accused_all), accuser, accused, accused), LENGTH_SHORT);
    }

    public void notificationWronglyAccusedAccuser(String accused) {
        this.toastUiThread(String.format(getString(R.string.wrongly_accused_accuser), accused), LENGTH_SHORT);
    }

    public void notificationWronglyAccusedAccused(String accuser) {
        this.toastUiThread(String.format(getString(R.string.wrongly_accused_accused), accuser), LENGTH_SHORT);
    }

    public void notificationWronglyAccusedAll(String accuser, String accused) {
        this.toastUiThread(String.format(getString(R.string.wrongly_accused_all), accuser, accused, accused, accuser), LENGTH_SHORT);
    }

    public void showPlayerAccused(String name) {
        this.toastUiThread("accused " + name + " of cheating", LENGTH_SHORT);
    }

    public void vibrate() {
        // Get instance of Vibrator from current Context
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        // Vibrate for 400 milliseconds
        v.vibrate(400);
    }


    public void handleShakeEvent() {
        //TODO: for shake action card
    }


    @Override
    protected void onStop() {
        super.onStop();

        if (game != null && game.getSession() != null) {
            game.getSession().close();
        }

        finish();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Add the following line to register the Session Manager Listener onResume
        mSensorManager.registerListener(mShakeDetector, mAccelerometer, SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    public void onPause() {
        // Add the following line to unregister the Sensor Manager onPause
        mSensorManager.unregisterListener(mShakeDetector);
        super.onPause();
    }


    private void toastUiThread(final String message) {
        toastUiThread(message, Toast.LENGTH_LONG);
    }

    private void toastUiThread(final String message, final int length) {
        this.runOnUiThread(() -> Toast.makeText(GameActivity.this, message, length).show());
    }

    public void showAccusePlayerDialog(String playerName) {
        DialogInterface.OnClickListener dialogClickListener = (dialog, which) -> {
            if (which == DialogInterface.BUTTON_POSITIVE) {
                game.accusePlayer(playerName);

            } else if (which == DialogInterface.BUTTON_NEGATIVE) {
                //No button clicked
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(String.format(getString(R.string.AccusePlayerDialog_question), playerName)).setPositiveButton(getString(R.string.buttonJa), dialogClickListener)
                .setNegativeButton(getString(R.string.buttonNein), dialogClickListener).show();
    }

    public Preferences getPreferences() {
        return preferences;
    }
}
