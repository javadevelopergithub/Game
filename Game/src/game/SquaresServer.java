package game;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Date;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;

public class SquaresServer extends Application implements SquaresConstants{
	//TODO the default server port is 8000
	//Server port
	private int port = 8000;
	
	// server made to allow many games at the same time
	// session ID
	private int sessionNo = 1; 
	
	@Override
	public void start(Stage primaryStage) {
		// creates log of the games played
		TextArea log = new TextArea();
		
		// creates and shows the stage of server
		Scene scene = new Scene(new ScrollPane(log), 450, 200);
		primaryStage.setTitle("SquaresServer");
		primaryStage.setScene(scene);
		primaryStage.show();
		
		// creates thread for game
		Thread thread = new Thread( () -> {
			try {
				// create a server socket
				ServerSocket serverSocket = new ServerSocket(port);
				
				// gives info about when a player connected
				Platform.runLater(() -> log.appendText(new Date() +
						": Server started at socket 8000\n"));
				
				//creates a session for every two players
				while (true) {
					Platform.runLater(() -> log.appendText(new Date() +
							": Wait for players to join session " + sessionNo + '\n'));
					
					// connect to player 1
					Socket player1 = serverSocket.accept();
					
					//indicates that a player joined
					Platform.runLater(() -> {
						log.appendText(new Date() + ": Player 1 joined session " 
					+ sessionNo + '\n');
						log.appendText("Player 1's IP address" +
					player1.getInetAddress().getHostAddress() + '\n');
						});
					
					// tells player 1 that he's player 1
					new DataOutputStream(
							player1.getOutputStream()).writeInt(PLAYER1);
					
					// connects to player 2
					Socket player2 = serverSocket.accept();
					
					Platform.runLater(() -> {
						// indicates that a player joined
						log.appendText(new Date() +
								": Player 2 joined session " + sessionNo + '\n');
						log.appendText("Player 2's IP address" +
								player2.getInetAddress().getHostAddress() + '\n');
						});
					
					// tells player 2 that he's player 2
					new DataOutputStream(
							player2.getOutputStream()).writeInt(PLAYER2);
					
					// displays the session and increments
					Platform.runLater(() -> 
					log.appendText(new Date() + 
							": Start a thread for session " + sessionNo++ + '\n'));
					
					// launches a new game session
					new Thread(new HandleASession(player1, player2)).start();
					}
				}
			catch(IOException ex) {
				ex.printStackTrace();
				}
			});
		thread.setDaemon(true);
		thread.start();
		}
	
	// game thread handling class
	class HandleASession implements Runnable, SquaresConstants {
		private Socket player1;
		private Socket player2;
		
		// input, output streams between players
		private DataInputStream fromPlayer1;
		private DataOutputStream toPlayer1;
		private DataInputStream fromPlayer2;
		private DataOutputStream toPlayer2;
		
		
		public HandleASession(Socket player1, Socket player2) {
			this.player1 = player1;
			this.player2 = player2;
		}
		
		public void run() {
			try {
				// creation of the input streams between players
				DataInputStream fromPlayer1 = new DataInputStream(
						player1.getInputStream());
				DataOutputStream toPlayer1 = new DataOutputStream(
						player1.getOutputStream());
				DataInputStream fromPlayer2 = new DataInputStream(
						player2.getInputStream());
				DataOutputStream toPlayer2 = new DataOutputStream(
						player2.getOutputStream());
	  
				// send a message to player 1 to start
				toPlayer1.writeInt(1);
				
				// serve the players
				while (true) {
					// Receive a move from player 1
					int row = fromPlayer1.readInt();
					int column = fromPlayer1.readInt();
					
					// if row is -1 then game has finished
					if (row == -1) break;
					
					// send move from player 1 to player 2
					else{
						sendMove(toPlayer2, row, column);
					}
					// receive move from player 2
					row = fromPlayer2.readInt();
					column = fromPlayer2.readInt();
	  
					// if row -1 game finished
					if (row == -1) break;
					
					// send move from player 2 to player 1
					else {
						sendMove(toPlayer1, row, column);
					}
				}
			}
			catch(IOException ex) {
				ex.printStackTrace();
			}
		}
		
		// method for sending moves
		private void sendMove(DataOutputStream out, int row, int column)
				throws IOException {
			out.writeInt(row); // send row
			out.writeInt(column); // send column
		}
		
	}
	
	public static void main(String[] args) {
		// just launches the stage
		launch(args);
	}
}
