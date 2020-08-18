1. Create a file `login.properties` in this directory, with your credentials:

```
email=info@example.com
password=secret
```

2. Compile the source code:

```
javac -cp ".:lib/*" com/stackexchange/toolbox/chat/MessagePoster.java
```

3. Run the program, with two arguments: the ID of the room, and the message you want to post.
The example below posts `This is a test.` in the Sandbox chatroom.

```
java -cp ".:lib/*" com.stackexchange.toolbox.chat.MessagePoster 1 "This is a test."
```
