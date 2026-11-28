import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;



public class ChatServer{

  enum State {
    
    REG,
    WAITING,
    ONLINE    
    
  }

  static class User{

    public String nick;
    public State state;
    public String sala;
    public ByteBuffer buffer;

    User(){

      this.state = State.REG;
      this.buffer = ByteBuffer.allocate(16384);

    }
    
  }   

  // A pre-allocated buffer for the received data
  static private final ByteBuffer buffer = ByteBuffer.allocate( 16384 );

  // Decoder for incoming text -- assume UTF-8
  static private final Charset charset = Charset.forName("UTF8");
  static private final CharsetDecoder decoder = charset.newDecoder();
  static Selector selector;
  static Set<String> nicks_usados = new HashSet<>();



  static public void main( String args[] ) throws Exception {
    // Parse port from command line
    int port = Integer.parseInt( args[0] );
    
    try {
      // Instead of creating a ServerSocket, create a ServerSocketChannel
      ServerSocketChannel ssc = ServerSocketChannel.open();

      // Set it to non-blocking, so we can use select
      ssc.configureBlocking( false );

      // Get the Socket connected to this channel, and bind it to the
      // listening port
      ServerSocket ss = ssc.socket();
      InetSocketAddress isa = new InetSocketAddress( port );
      ss.bind( isa );

      // Create a new Selector for selecting
      selector = Selector.open();

      // Register the ServerSocketChannel, so we can listen for incoming
      // connections
      ssc.register( selector, SelectionKey.OP_ACCEPT );
      System.out.println( "Listening on port "+port );

      while (true) {
        // See if we've had any activity -- either an incoming connection,
        // or incoming data on an existing connection
        int num = selector.select();

        // If we don't have any activity, loop around and wait again
        if (num == 0) {
          continue;
        }

        // Get the keys corresponding to the activity that has been
        // detected, and process them one by one
        Set<SelectionKey> keys = selector.selectedKeys();
        Iterator<SelectionKey> it = keys.iterator();
        while (it.hasNext()) {
          // Get a key representing one of bits of I/O activity
          SelectionKey key = it.next();

          // What kind of activity is it?
          if (key.isAcceptable()) {

            // It's an incoming connection.  Register this socket with
            // the Selector so we can listen for input on it
            Socket s = ss.accept();
            System.out.println( "Got connection from "+s );

            // Make sure to make it non-blocking, so we can use a selector
            // on it.
            SocketChannel sc = s.getChannel();
            sc.configureBlocking( false );

            // Register it with the selector, for reading
            sc.register( selector, SelectionKey.OP_READ, new User());

          } else if (key.isReadable()) {

            SocketChannel sc = null;

            try {

              // It's incoming data on a connection -- process it
              sc = (SocketChannel)key.channel();
              boolean ok = processInput( sc );

              // If the connection is dead, remove it from the selector
              // and close it
              if (!ok) {
                key.cancel();

                Socket s = null;
                try {
                  s = sc.socket();
                  System.out.println( "Closing connection to "+s );
                  s.close();
                } catch( IOException ie ) {
                  System.err.println( "Error closing socket "+s+": "+ie );
                }
              }

            } catch( IOException ie ) {

              // On exception, remove this channel from the selector
              key.cancel();

              try {
                sc.close();
              } catch( IOException ie2 ) { System.out.println( ie2 ); }

              System.out.println( "Closed "+sc );
            }
          }
        }

        // We remove the selected keys, because we've dealt with them.
        keys.clear();
      }
    } catch( IOException ie ) {
      System.err.println( ie );
    }
  }


  // Just read the message from the socket and send it to stdout
  static private boolean processInput( SocketChannel sc ) throws IOException {
    
    SelectionKey atual = sc.keyFor(selector);

    User u = ((User) atual.attachment());

    ByteBuffer userbuffer = u.buffer;

    int fechado = sc.read( userbuffer );
    userbuffer.flip();

    // If no data, close the connection
    if (fechado==-1) {
      
      nicks_usados.remove(u.nick);

      return false;
    }

    Set<SelectionKey> users = selector.keys();

    int i = userbuffer.position();

    while (userbuffer.position() < userbuffer.limit()) {

      boolean achou = false;

      for (int j = userbuffer.position(); j < userbuffer.limit(); j++){

        if (userbuffer.get(j) == '\n'){

          i = j;

          achou = true;

          break;

        }        

      }

      if (achou){

          byte[] linha = new byte[i-userbuffer.position()];

          userbuffer.get(linha);
        
          String linha_str = new String(linha, charset);

          if (e_comando(linha_str)){

            String[] pedacos = linha_str.split(" ");

            String resposta = comandos(atual, pedacos);

            enviar(sc, resposta);

            if (resposta.equals("BYE")){

              return false;

            }

          }else{

            if (!linha_str.isEmpty() && linha_str.charAt(0) == '/')

              linha_str = linha_str.substring(1);

            if (u.state.equals(State.REG) || u.state.equals(State.WAITING)){

              enviar(sc, "ERROR");

            }

          }

          userbuffer.get();

        } 

        else{

          break;

        }
      
    }

    userbuffer.compact();

    /*if (((User) atual.attachment()).nick == null){

      String message = decoder.decode(userbuffer).toString();
      userbuffer.compact();
      System.out.print("New username:"+ " " + message );

      String nickname = message.trim();

      ((User) atual.attachment()).nick = nickname;

      return true;

    }*/

    // Decode and print the message to stdout
    Iterator<SelectionKey> it = users.iterator();
    /* 
    while (it.hasNext()) {
      
      SelectionKey user = it.next();

      if (user.channel() instanceof ServerSocketChannel){

        continue;

      }

      //if (user.equals(sc.keyFor(selector))) continue;

      SocketChannel answer;

      answer = (SocketChannel)user.channel();

      buffer.clear();

      String nick = ((User) atual.attachment()).nick;

      String send = nick + ": " +  message;

      buffer.put(send.getBytes(charset));

      buffer.flip();

      answer.write(buffer);

    } */

    return true;

  }


  static public String comandos(SelectionKey user, String[] pedacos){

    String comando = pedacos[0];

    User u = (User) user.attachment();

    if (comando.equals("/nick")){

      if (pedacos.length != 2) return "ERROR"; 

      if (nicks_usados.contains(pedacos[1])) return "ERROR";

      if (u.state.equals(State.REG)) 
        
        u.state = State.WAITING;

      else

        nicks_usados.remove(u.nick);

      u.nick = pedacos[1];
      nicks_usados.add(pedacos[1]);

      return "OK";

    }

    if (comando.equals("/bye")){

      if (pedacos.length != 1) return "ERROR";

      nicks_usados.remove(u.nick);

      return "BYE";

    }

    return "ERROR";

  }

  static public boolean e_comando(String linha){

    if (linha.length() == 1 && linha.charAt(0) == '/') return true;

    if (!linha.isEmpty() && linha.charAt(0) == '/' && linha.length() >= 2 && linha.charAt(1) != '/' ) return true;

    return false;

  }

  static public void enviar(SocketChannel channel, String mensgem) throws IOException{

    ByteBuffer buffer = charset.encode(mensgem + "\n");

    channel.write(buffer);

  }

}
