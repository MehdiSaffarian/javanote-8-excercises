
package netgame.newchat;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.control.TextArea;
import javafx.scene.control.Alert;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Priority;
import javafx.geometry.Insets;

import java.util.Optional;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.TreeMap;

import netgame.common.*;

/**
 * This class represents a client for a "chat room" application.  The chat
 * room is hosted by a server running on some computer.  The user of this
 * program must know the host name (or IP address) of the computer that
 * hosts the chat room.  When this program is run, it asks for that
 * information and for the name that the user wants to use in the chat
 * room.  Then, it opens a window that has an input box where the
 * user can enter messages to be sent to the chat room.  The message is 
 * sent when the user presses return in the input box or when the
 * user clicks a Send button.  There is also a text area that shows 
 * a transcript of all messages from participants in the chat room.
 * <p>The user can also send private messages to individual users.
 * The user selects the recipient's name from a pop-up list of
 * connected users.
 * <p>Participants in the chat room are represented by ID numbers
 * that are assigned to them by the server when they connect. They
 * also have names which they select.
 */
public class NewChatRoomWindow extends Application {
    
    public static void main(String[] args) {
        launch(args);
    }
    //---------------------------------------------------------------------------------
    
    private final static int PORT = 37830; // The ChatRoom port number; can't be 
                                           // changed here unless the ChatRoomServer
                                           // program is also changed.

    private TextField messageInput;   // For entering messages to be sent to the chat room
    private Button sendButton;        // Sends the contents of the messageInput.
    private Button quitButton;        // Leaves the chat room cleanly, by sending a DisconnectMessage
    
    private TextArea transcript;      // Contains all messages sent by chat room participant, as well
                                      //    as a few additional status messages, 
                                      //    such as when a new user arrives.
    
    private ChatClient connection;    // Represents the connection to the Hub; used to send messages;
                                      // also receives and processes messages from the Hub.
    
    private volatile boolean connected; // This is true while the client is connected to the hub.
    
    private volatile String myName; // The name that this client uses in the chat room.
                                    // Originally selected by the user, but might be modified
                                    // if there is already a client of the same name connected
                                    // to the Hub.

    private volatile TreeMap<Integer,String> clientNameMap = new TreeMap<Integer, String>();
                                    // The clientNameMap maps client ID numbers to the names that they are
                                    // using in the chat room.  Every time a client connects or disconnects,
                                    // the Hub sends a new, modified name map to each connected client.  When
                                    // that message is received, the clientNameMap is replaced with the new value,
                                    // and the content of the clientList is replaced with info from the nameMap.

    private ComboBox<String> clientList;    // List of connected client names, where the user can select
                                            //   the client who is to receive the private message.

    private TextField privateMessageInput;  // For entering messages to be set to individual clients.
    private Button sendPrivateButton;   // Sends the contents of privateMesssageInput to the user selected
                                        //   in the clientList.

    /**
     * Gets the host name (or IP address) of the chat room server from the
     * user and then opens the main window.  The program ends when the user
     * closes the window.
     */
    public void start( Stage stage ) {
        
        TextInputDialog question = new TextInputDialog();
        question.setHeaderText("Enter the host name of the\ncomputer that hosts the chat room.");
        question.setContentText("Host Name:");
        Optional<String> response = question.showAndWait();
        if ( ! response.isPresent() )
            System.exit(0);
        String host = response.get().trim();
        if (host == null || host.trim().length() == 0)
            System.exit(0);
        
        question = new TextInputDialog();
        question.setHeaderText("Enter the name that you want\nto use in the chat room.");
        question.setContentText("Your Name:");
        response = question.showAndWait();
        if ( ! response.isPresent() )
            System.exit(0);
        myName = response.get().trim();
        if (myName == null || myName.trim().length() == 0)
            System.exit(0);

        transcript = new TextArea();
        transcript.setPrefRowCount(30);
        transcript.setPrefColumnCount(60);
        transcript.setWrapText(true);
        transcript.setEditable(false);

        sendButton = new Button("send to all");
        quitButton = new Button("quit");
        messageInput = new TextField();
        messageInput.setPrefColumnCount(40);
        sendButton.setOnAction( e -> doSend() );
        quitButton.setOnAction( e -> doQuit() );
        sendButton.setDisable(true);
        messageInput.setEditable(false);
        messageInput.setDisable(true);
        
        sendPrivateButton = new Button("send to one");
        sendPrivateButton.setOnAction( e -> doSendPrivateMessage() );
        privateMessageInput = new TextField();
        privateMessageInput.setPrefColumnCount(30);
        clientList = new ComboBox<String>();
        clientList.setEditable(false);
        clientList.getItems().add("(no one available)");
        clientList.getSelectionModel().select(0);
        
        HBox bottomRow1 = new HBox(8, new Label("YOU SAY:"), messageInput, sendButton, quitButton);
        HBox.setHgrow(messageInput, Priority.ALWAYS);
        HBox.setMargin(quitButton, new Insets(0,0,0,50));
        
        HBox bottomRow2 = new HBox(8, new Label("SAY:"), privateMessageInput, 
                                       new Label(" To: "), clientList, sendPrivateButton);
        HBox.setHgrow(privateMessageInput, Priority.ALWAYS);
        
        VBox bottom = new VBox(8, bottomRow1, bottomRow2);
        bottom.setPadding(new Insets(8));
        bottom.setStyle("-fx-border-color: black; -fx-border-width:2px");
        BorderPane root = new BorderPane(transcript);
        root.setBottom(bottom);
        
        stage.setScene( new Scene(root) );
        stage.setTitle("Networked Chat");
        stage.setResizable(false);
        stage.setOnHidden( e -> doQuit() );
        stage.show();
        
        /* The next two lines make the sendButton and sendPrivateButton into the
         * default button for the window exactly when the corresponding input box
         * is focussed.  This means that the user can just hit return while 
         * typing in an input box to send the message. */
        
        messageInput.focusedProperty().addListener( 
                        (target,oldVal,newVal) -> sendButton.setDefaultButton(newVal) );
        privateMessageInput.focusedProperty().addListener( 
                         (target,oldVal,newVal) -> sendPrivateButton.setDefaultButton(newVal) );
        
        new Thread() {
                // This is a thread that opens the connection to the server.  Since
                // that operation can block, it's not done directly in the constructor.
                // Once the connection is established, the user interface elements are
                // enabled so the user can send messages.  The Thread dies after
                // the connection is established or after an error occurs.
            public void run() {
                try {
                    addToTranscript("Connecting to " + host + " ...");
                    connection = new ChatClient(host);
                    connected = true;
                    Platform.runLater( () -> {
                        messageInput.setEditable(true);
                        messageInput.setDisable(false);
                        sendButton.setDisable(false);
                        messageInput.requestFocus();
                    });
                }
                catch (IOException e) {
                    Platform.runLater( () -> {
                        addToTranscript("Connection attempt failed.");
                        addToTranscript("Error: " + e);
                    });
                }
            }
        }.start();

    }
    


    /**
     * A ChatClient connects to the Hub and is used to send messages to
     * and receive messages from a Hub.  Four types of message are
     * received from the Hub.  A ForwardedMessage represents a message
     * that was entered by some user and sent to all users of the
     * chat room.  A PrivateMessage represents a message that was
     * sent by another user only to this user.  A ClientConnectedMessage
     * is sent when a new user enters the room.  A ClientDisconnectedMessage
     * is sent when a user leaves the room.
     */
    private class ChatClient extends Client {
        
        /**
         * Opens a connection the chat room server on a specified computer.
         */
        ChatClient(String host) throws IOException {
            super(host, PORT);
        }
        
        /**
         * Responds when a message is received from the server.
         */
        protected void messageReceived(Object message) {
            if (message instanceof ForwardedMessage) {
                ForwardedMessage fm = (ForwardedMessage)message;
                String senderName = clientNameMap.get(fm.senderID);
                addToTranscript(senderName + " SAYS:  " + fm.message);
            }
            else if (message instanceof PrivateMessage) {
                PrivateMessage pm = (PrivateMessage)message;
                String senderName = clientNameMap.get(pm.senderID);
                addToTranscript("PRIVATE MESSAGE FROM " + senderName + ":  " + pm.message);
            }
            else if (message instanceof ClientConnectedMessage) {
                ClientConnectedMessage cm = (ClientConnectedMessage)message;
                addToTranscript('"' + cm.nameMap.get(cm.newClientID) + "\" HAS JOINED THE CHAT ROOM.");
                newNameMap(cm.nameMap);
            }
            else if (message instanceof ClientDisconnectedMessage) {
                ClientDisconnectedMessage dm = (ClientDisconnectedMessage)message;
                addToTranscript('"' + clientNameMap.get(dm.departingClientID) + "\" HAS LEFT THE CHAT ROOM.");
                newNameMap(dm.nameMap);
            }
        }
        
        /**
         * This method is part of the connection set up.  It sends the user's selected
         * name to the hub by writing that name to the output stream.  The hub will
         * respond by sending the name back to this client, possibly modified if someone
         * is the chat room is already using the selected name.
         */
        protected void extraHandshake(ObjectInputStream in, ObjectOutputStream out) throws IOException {
            try {
                out.writeObject(myName);  // Send user's name request to the server. 
                myName = (String)in.readObject();  // Get the actual name from the server.
            }
            catch (Exception e) {
                throw new IOException("Error while setting up connection: " + e);
            }
        }

        /**
         * Called when the connection to the client is shut down because of some
         * error message.  (This will happen if the server program is terminated.)
         */
        protected void connectionClosedByError(String message) {
            addToTranscript("Sorry, communication has shut down due to an error:\n     " + message);
            Platform.runLater( () -> {
	            sendButton.setDisable(true);
	            messageInput.setDisable(true);
	            messageInput.setEditable(false);
	            messageInput.setText("");
	            sendPrivateButton.setDisable(true);
	            privateMessageInput.setDisable(true);
	            privateMessageInput.setEditable(false);
            });
            connected = false;
            connection = null;
        }
        
        // Note:  the methods playerConnected() and playerDisconnected(), which where present here
        // in ChatRoomWindow, were removed, since their functionality (to announce arrivals
        // and departures) has been taken over by ClientConnectedMessage and ClientDisconnectedMessage.

    } // end nested class ChatClient
    
  
    /**
     * Adds a string to the transcript area, followed by a blank line.
     */
    private void addToTranscript(String message) {
        Platform.runLater( () -> transcript.appendText(message + "\n\n") );
    }
    
    
    /**
     * Called when the user clicks the Quit button or closes
     * the window by clicking its close box. Called from the
     * application thread.
     */
    private void doQuit() {
        if (connected)
            connection.disconnect();  // Sends a DisconnectMessage to the server.
        try {
            Thread.sleep(500); // Time for DisconnectMessage to actually be sent.
        }
        catch (InterruptedException e) {
        }
        System.exit(0);
    }

    /**
     * This method is called when a ClientConnectedMessage or ClientDisconnectedMessage
     * is received from the hub.  Its job is to save the nameMap that is part of the
     * message and use it to rebuild the contents of the ComboBox, clientList, where
     * the user selects the recipient of a private message.  It also enables or
     * disables the private message input box and send button, depending on whether
     * there are any possible message recipients.
     * @param nameMap the new nameMap, which will replace the value of clientNameMap.
     */
    private void newNameMap(final TreeMap<Integer,String> nameMap) {
        Platform.runLater( () ->  {
            clientNameMap = nameMap;
            String currentlySelected = clientList.getSelectionModel().getSelectedItem();
            clientList.getItems().clear();
            boolean someoneIsThere = false;
            boolean currentSelectionIsThere = false;
            for (String str: nameMap.values()) {
                if (!str.equals(myName)) {
                    clientList.getItems().add(str);
                    someoneIsThere = true;
                }
                if (str.equals(currentlySelected))
                    currentSelectionIsThere = true;
            }
            privateMessageInput.setEditable(someoneIsThere);
            privateMessageInput.setDisable(!someoneIsThere);
            sendPrivateButton.setDisable(!someoneIsThere);
            if (!someoneIsThere)
                clientList.getItems().add("(no one available)");
            if (currentSelectionIsThere)
                clientList.getSelectionModel().select(currentlySelected);
            else
                clientList.getSelectionModel().select(0);
        });
    }
    

    /** 
     * Send the string entered by the user as a message
     * to the Hub, using the ChatClient that handles communication
     * for this ChatRoomWindow.  Note that the string is not added
     * to the transcript here.  It will get added after the Hub
     * receives the message and broadcasts it to all clients,
     * including this one.  Called from the application thread.
     */
    private void doSend() {
        String message = messageInput.getText();
        if (message.trim().length() == 0)
            return;
        connection.send(message);
        messageInput.selectAll();
        messageInput.requestFocus();
    }

    
    private void doSendPrivateMessage() {
        // Send a private message to a specified recipient.
        // If the private message inputbox is empty, nothing is done.
        String message = privateMessageInput.getText();
        if (message.trim().length() == 0)
            return;
        String recipient = clientList.getSelectionModel().getSelectedItem(); // name of recipient.
        int recipientID = -1;  // The ID number of the recipient
        for (int id : clientNameMap.keySet()) {
            // Search the clientNameMap to find the ID number
            // corresponding to the specified recipient name.
            if (recipient.equals(clientNameMap.get(id))) {
                recipientID = id;
                break;
            }
        }
        if (recipientID == -1) {
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    "Funny... The selected recipient\ndoesn't seem to exit???");
            alert.showAndWait();
            return;
        }
        connection.send(new PrivateMessage(recipientID,message));
        addToTranscript("Sent to " + recipient + ":  " + message);
    }
    

} // end class NewChatRoomWindow
