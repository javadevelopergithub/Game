package game;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.SequentialTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;

public class SquaresClient extends Application implements SquaresConstants{
	private boolean myTurn = false;
	
	// displays which player you are
	private Label lblPlayer = new Label();
	
	// Continue to play?
	private boolean playing = true;

	// Displays who's turn it is, who won
	private Label lblStatus = new Label();
	
	// Input and output streams from/to server
	private DataInputStream fromServer;
	private DataOutputStream toServer;
	
	// Wait for the player to mark a cell
	private boolean wait = true;
	
	//TODO the default host name and port number are 127.0.0.1 and 8000
	// host name
	private String host = "127.0.0.1";
	// port number
	private int port = 8000;
	
	//main pane
	BorderPane pane = new BorderPane();
	
	// the size of the board. Static
	int size = 2;
	
	// indicates who's turn it is
	char turn = '1';
	
	// last move coordinates
	int[] lastMove = new int[2];
	
	// grid for all the cell objects
	Cell[][] cell;
	
	// game Pane where the cells are displayed
	GridPane gamePane;
	
	// point text object
	Text txtPoints = new Text("");
	
	// player 1 score
	int points1 = 0;
	
	// player 2 score
	int points2 = 0;
	
	// the transition object for the fade transitions on getting points
	SequentialTransition st = new SequentialTransition ();
	
	@Override
	public void start(Stage stage) throws Exception {
		
		//initializes the grid with game cells
		gamePane = new GridPane();
		cell = new Cell[size][size];
		for (int i = 0; i < size; i++)
			for (int j = 0; j < size; j++){
				cell[i][j] = new Cell(' ');
				gamePane.add(cell[i][j], i, j);
			}
		// sets it in the center
		gamePane.setAlignment(Pos.CENTER);
		pane.setCenter(gamePane);
		
		// creates and places at teh bottom container for the game messages
		VBox lblBox = new VBox();
		lblBox.getChildren().addAll(txtPoints, lblStatus, lblPlayer);
		pane.setBottom(lblBox);
		
		// pane size is adjusted in accordance to the grid size and is later unchangeable
		int paneWidth = 50 * size + 100;
		int paneHeight = 50 * size + 200;
		
		// sets the main pane as the scene's pane
		Scene scene = new Scene(pane, paneWidth, paneHeight);
		stage.setScene(scene);
		// stage isn't resizable
		stage.setResizable(false);
		// shows the game window
		stage.show();
		
		// Connects to server and starts the game
	    startGame();
	    }
	
	private void startGame() {
		try {
			// creates a socket to server
			Socket socket = new Socket(host, port);
			
			// create input stream with server
			fromServer = new DataInputStream(socket.getInputStream());
			
			// create output stream with server
			toServer = new DataOutputStream(socket.getOutputStream());
		}
		catch (Exception ex) {
			ex.printStackTrace();
		}
		
		// the control happens on a separate thread
		Thread thread = new Thread(() -> {
			try {
				// server sends indication of which player you are
				int player = fromServer.readInt();

				// if player 1
				if (player == PLAYER1) {
					Platform.runLater(() -> {
						lblPlayer.setText("Player 1 color: green");
						lblStatus.setText("waiting for player 2");
					});
					
					// any data sent is indication of game starting
					fromServer.readInt(); 
	  
					// start indication for player
					Platform.runLater(() -> 
					lblStatus.setText("Both players present. You can play"));
					
					// player 1 turn is made true
					myTurn = true;
				}
				// if player 2
				else if (player == PLAYER2) {
					Platform.runLater(() -> {
						lblPlayer.setText("Player 2 color: black");
						lblStatus.setText("wait for player 1 to move");
					});
				}
				
				// continuation of game
				while (playing) {      
					if (player == PLAYER1) {
						waitForPlayerAction(); // Wait for player 1 to move
						sendMove(); // Send the move to the server
						receiveInfoFromServer(); // Receive info from the server
					}
					else if (player == PLAYER2) {
						receiveInfoFromServer(); // Receive info from the server
						waitForPlayerAction(); // Wait for player 2 to move
						sendMove(); // Send player 2's move to the server
						
					}
				}
			}
			catch (Exception ex) {
				ex.printStackTrace();
			}
		});
		// make thread daemon so that if the stage is closed, the thread terminates
		thread.setDaemon(true);
		thread.start();
	}
	private void waitForPlayerAction() throws InterruptedException {
		// thread waits for move from player
		    while (wait) {
		      Thread.sleep(100);
		    }
		    wait = true;
	}
	private void sendMove() throws IOException {
		toServer.writeInt(lastMove[1]); // send the selected row
		toServer.writeInt(lastMove[0]); // send the selected column
	}
	private void receiveInfoFromServer() throws IOException {
		// receive move
		receiveMove();
		Platform.runLater(() -> {
			if (isFull()){
				// send row = -1 to indicate game finished to server
				lastMove[1] = -1;
				wait = false;
				lblStatus.setText("");
			}
			else
				lblStatus.setText("My turn");
		});
		// changes to player's turn
		myTurn = true;
		
		}
	private void receiveMove() throws IOException {
		// get the other player's move
		lastMove[1] = fromServer.readInt();
		lastMove[0] = fromServer.readInt();
		// execute the move
		Platform.runLater(() -> doMoveFromServer());
	}
	private void doMoveFromServer() {
		// get appropriate cell and do the move
		Cell target = (Cell)gamePane.getChildren().get(lastMove[0] * size + lastMove[1] );
		doMove(target);
	}
	public void doMove(Cell target){
		// stop all animations and change their node opacity to 0
		st.stop();
		for (Animation anim : st.getChildren())
			((FadeTransition)anim).getNode().setOpacity(0);
			
		// the cell is set to the side of the current player's turn
		target.side = turn;
		
		// allocates the points to the appropriate player
		if (turn == '1')
			points1 += getPoints();
		else if (turn == '2')
			points2 += getPoints();
		// changes turn
		turn = (turn == '1') ? '2' : '1';
		
		// paints the square in the cell
		target.paintSquare();
		// checks if the game is over
		if (isFull()){
			// indicates that no longer playing
			playing = false;
			// sets turn to none
			turn = ' ';
			// writes end game message
			String message = "The game has finished. ";
			if (points1 == points2) 
				message += "Draw.";
			else{
				message += (points1 > points2) ? "Green" : "Black";
				message += " player won.";
			}
			message += "\n Green: " + points1 + " Black: " + points2;
			txtPoints.setText(message);
			
			// set status to empty string
			lblStatus.setText("");
		}
		// if game not over then writes points
		else{
			txtPoints.setText("Green: " + points1 + " black: " + points2);
		}
	}
	
	public boolean isFull() {
		// checks if cells are full by checking each cells side
		ObservableList<Node> list = gamePane.getChildren();
		for (Node square : list)
			if (((Cell)square).side == ' ') return false;
		return true;
	}
	public int getPoints(){
		// removes all animations
		st.getChildren().clear();
		
		// int object for summing points
		int points = 0;
		
		// iterates by cell horizontally and vertically to get points and animations
		for (int i = 0; i < size; i++){
			// vertical iteration if same y skips
			if (i != lastMove[1]){
				//gets iteration cell
				Cell temp = cell[lastMove[0]][i];
				// y distance between last move
				int diff = Math.abs(lastMove[1] - i);
				
				//if same side continues
				if (temp.side == cell[lastMove[0]][lastMove[1]].side){
					//check for squares to the left and gets points, animations
					int left = lastMove[0] - diff;
					if(left >= 0 && left < size)
						if (cell[left][i].side == cell[left][lastMove[1]].side &&
							cell[left][i].side == temp.side){ 
							int tempPoints = (int)Math.pow(diff + 1, 2);
							points += tempPoints;
							if (i < lastMove[1])
								getAnimation(left ,i ,lastMove[0], i, lastMove[0], lastMove[1], left, lastMove[1], tempPoints);
							else
								getAnimation(left, lastMove[1], lastMove[0], lastMove[1], lastMove[0], i, left ,i, tempPoints);
						}
					
					//check for squares to the right and gets points, animations
					int right = lastMove[0] + diff;
					if (right >= 0 && right < size)
						if (cell[right][i].side == cell[right][lastMove[1]].side &&
						cell[right][i].side == temp.side){
							int tempPoints = (int)Math.pow(diff + 1, 2);
							points += tempPoints;
							if (i < lastMove[1])
								getAnimation(lastMove[0], i, right, i, right, lastMove[1], lastMove[0], lastMove[1], tempPoints);
							else
								getAnimation(lastMove[0], lastMove[1], right, lastMove[1], right, i, lastMove[0], i, tempPoints);
						}
					
					//check for diamonds vertically and gets points, animations
					int dLeft = lastMove[0] - diff / 2;
					int dRight = lastMove[0] + diff / 2;
					if (diff % 2 == 0 && dLeft >= 0 && dRight < size){
						int mid = (i + lastMove[1]) / 2;
						if (cell[dRight][mid].side == cell[dLeft][mid].side && 
							cell[dRight][mid].side == temp.side){
							int tempPoints = (int)Math.pow(diff + 1, 2);
							points += tempPoints;
							if (i < lastMove[1])
								getAnimation(lastMove[0], i, dRight, mid, lastMove[0], lastMove[1], dLeft, mid, tempPoints);
							else
								getAnimation(lastMove[0], lastMove[1], dRight, mid, lastMove[0], i, dLeft, mid, tempPoints);
							
						}
					}
				}
			
			}
			
			// horizontal iteration just for diamonds and gets points, animations
			//if same x skips
			if (i != lastMove[0]){
				Cell temp = cell[i][lastMove[1]];
				int diff = Math.abs(lastMove[0] - i);
				if (temp.side == cell[lastMove[0]][lastMove[1]].side){
					int up = lastMove[1] - diff / 2;
					int down = lastMove[1] + diff / 2;
					if (diff % 2 == 0 && up >= 0 && down < size){
						int mid = (i + lastMove[0]) / 2;
						if (cell[mid][up].side == cell[mid][down].side && 
							cell[mid][up].side == temp.side){
							int tempPoints = (int)Math.pow(diff + 1, 2);
							points += tempPoints;
							if (i < lastMove[0])
								getAnimation(mid, up, lastMove[0], lastMove[1], mid, down, i, lastMove[1], tempPoints);
							else
								getAnimation(mid, up, i, lastMove[1], mid, down, lastMove[0], lastMove[1], tempPoints);
						}
					}
				}
			}
		}
		
		// plays all the animations
		st.play();
		// returns points
		return points;
	}
	
	public FadeTransition getAnimation(int topLeftX, int topLeftY, int topRightX, int topRightY, int bottomRightX, int bottomRightY, int bottomLeftX, int bottomLeftY, int score){
		Polygon rect = new Polygon();
		// if the rectangle is a square then topLeft indicates it's corner with min x, min y, coordinates.
		// if it's a diamond then topLeft corner has the min y coordinates and the naming no longer corresponds to the figure.
		// after that all other points are placed clockwise. The points need to be placed this way for the method to work.
		rect.getPoints().addAll(new Double(cell[topLeftX][topLeftY].getLayoutX()+ cell[topLeftX][topLeftY].getWidth() / 2), 
				new Double(cell[topLeftX][topLeftY].getLayoutY() + cell[topLeftX][topLeftY].getHeight() / 2), 
				
				new Double(cell[topRightX][topRightY].getLayoutX()+ cell[topRightX][topRightY].getWidth() / 2), 
				new Double(cell[topRightX][topRightY].getLayoutY() + cell[topRightX][topRightY].getHeight() / 2), 
				
				new Double(cell[bottomRightX][bottomRightY].getLayoutX()+ cell[bottomRightX][bottomRightY].getWidth() / 2), 
				new Double(cell[bottomRightX][bottomRightY].getLayoutY() + cell[bottomRightX][bottomRightY].getHeight() / 2), 
				
				new Double(cell[bottomLeftX][bottomLeftY].getLayoutX()+ cell[bottomLeftX][bottomLeftY].getWidth() / 2), 
				new Double(cell[bottomLeftX][bottomLeftY].getLayoutY() + cell[bottomLeftX][bottomLeftY].getHeight() / 2));
		
		//setting square colours
		rect.setFill(Color.BLACK);
		rect.setStroke(Color.BLACK);
		
		//preparing the animation coordinates and size of text values
		double animX = 0;
		double animY = 0;
		double textSize = 0;
		
		//setting them in accordance to figure size and type
		ObservableList<Double> points = rect.getPoints();
		// if square 
		if (topLeftY == topRightY) {
			animX = (points.get(0) + points.get(2)) / 2; //topLeftX, topRightX mid point
			animY = (points.get(1) + points.get(5)) / 2; //topLeftY, bottomRightY mid point
			textSize = points.get(2) - points.get(0); //topRightX - topLeftX
			// size of text set by observation
			if (score > 9)
				textSize *= 0.9;
		}
		else{
			// is diamond
			animX = points.get(0); //topLeftX
			animY = points.get(3); //topRightY
			textSize = (points.get(2) - points.get(6)) / 2; // the distance between the min x and max x divided by 2
			textSize *= (score > 16) ? 1.15 : 1.25;
		}
		
		//setting the text in the middle of the animation
		Text scrText = new Text(score + "");
		scrText.setFill(Color.WHITE);
		pane.getChildren().add(scrText);
		scrText.setStyle("-fx-font: " + (int)textSize + " arial;");
		
		//putting the shape and the text in the middle of the animation with a stack pane
		StackPane sp = new StackPane();
		sp.getChildren().addAll(rect, scrText);
		sp.setLayoutX(animX);
		sp.setLayoutY(animY);
		sp.setOpacity(0);
		sp.setMouseTransparent(true);
		
		// setting up animation
		FadeTransition ft = new FadeTransition(Duration.millis(1000), sp);
		ft.setFromValue(0);
		ft.setToValue(0.75);
		ft.setCycleCount(4);
		ft.setAutoReverse(true);
		
		// adding the animation to the queue of other animations and the animation node to the pane
		st.getChildren().add(ft);
		pane.getChildren().add(sp);
		
		return ft;
	}	
	public class Cell extends Pane {
		//Cell stores the information of which player pressed it
		char side = ' ';
		
		public Cell(char side){
			// can be created with a side already assigned but this option isn't used
			this.side = side;
			
			//sets the cell size and border color
			this.setPrefSize(50, 50);
			this.setStyle("-fx-border-color: black");
			
			// when the cell is clicked changes game status, sends move to server and does the move on the board
			this.setOnMouseClicked(e -> handleMouseClick());
		}
		
		public void paintSquare() {
			// creates a square and sets it's size, place, shape 
			Rectangle square = new Rectangle(40, 40);
			square.setX(5);
			square.setY(5);
			square.setArcWidth(12.5);
			square.setArcHeight(12.5);
			
			// paints to the color of the player
			if (side == '1'){
				square.setFill(Color.GREEN);
				square.setStroke(Color.GREEN);
			}
			else if (side == '2'){
				square.setFill(Color.BLACK);
				square.setStroke(Color.BLACK);
			}
			this.getChildren().add(square);
		}
		private void handleMouseClick() {
			// checks if the square hasn't been pressed and if it's the player's turn
			if (this.side == ' ' && myTurn){
				// changes turns
				myTurn = false;
				lblStatus.setText("wait for the other player to move");
				// allows for move to be sent to server
				wait = false;
				
				// stores the last move (meant for getting points, animations)
				lastMove[0] = GridPane.getColumnIndex(this);
				lastMove[1] = GridPane.getRowIndex(this);
				
				// the continuation is the same as when the move is received from the server
				// so is encapsulated in a different method
				doMove(this);
			}
		}
	}
	public static void main(String[] args) {
		//main method just starts the program
		Application.launch(args);
	}

}
