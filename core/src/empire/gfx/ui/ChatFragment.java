package empire.gfx.ui;

import empire.game.Actions;
import empire.gfx.EmpireCore;
import io.anuke.arc.Core;
import io.anuke.arc.collection.Array;
import io.anuke.arc.graphics.Color;
import io.anuke.arc.graphics.g2d.BitmapFont;
import io.anuke.arc.graphics.g2d.Draw;
import io.anuke.arc.graphics.g2d.Fill;
import io.anuke.arc.graphics.g2d.GlyphLayout;
import io.anuke.arc.input.KeyCode;
import io.anuke.arc.scene.Group;
import io.anuke.arc.scene.ui.Label;
import io.anuke.arc.scene.ui.Label.LabelStyle;
import io.anuke.arc.scene.ui.TextField;
import io.anuke.arc.scene.ui.layout.Table;
import io.anuke.arc.scene.ui.layout.Unit;
import io.anuke.arc.util.Align;
import io.anuke.arc.util.Time;

import static io.anuke.arc.Core.input;
import static io.anuke.arc.Core.scene;

public class ChatFragment extends Table{
    private final static int messagesShown = 10;
    private Array<ChatMessage> messages = new Array<>();
    private float fadetime;
    private boolean chatOpen = false;
    private TextField chatfield;
    private Label fieldlabel = new Label(">");
    private BitmapFont font;
    private GlyphLayout layout = new GlyphLayout();
    private float offsetx = Unit.dp.scl(4), offsety = Unit.dp.scl(4), fontoffsetx = Unit.dp.scl(2), chatspace = Unit.dp.scl(50);
    private Color shadowColor = new Color(0, 0, 0, 0.4f);
    private float textspacing = Unit.dp.scl(10);
    private Array<String> history = new Array<>();
    private int historyPos = 0;
    private int scrollPos = 0;

    private static final int maxTextLength = 210;

    public ChatFragment(){
        super();

        setFillParent(true);
        font = scene.skin.getFont("default");

        visible(() -> {
            if(!EmpireCore.net.active() && messages.size > 0){
                clearMessages();

                if(chatOpen){
                    hide();
                }
            }

            return EmpireCore.net.active();
        });

        update(() -> {

            if(EmpireCore.net.active() && input.keyTap(KeyCode.ENTER)){
                toggle();
            }

            if(chatOpen){
                if(input.keyTap(KeyCode.UP) && historyPos < history.size - 1){
                    if(historyPos == 0) history.set(0, chatfield.getText());
                    historyPos++;
                    updateChat();
                }
                if(input.keyTap(KeyCode.DOWN) && historyPos > 0){
                    historyPos--;
                    updateChat();
                }
                //scrollPos = (int)Mathf.clamp(scrollPos + input.axis(), 0, Math.max(0, messages.size - messagesShown));
            }
        });

        history.insert(0, "");
        setup();
    }

    public void build(Group root){
        root.addChild(this);
    }

    public void clearMessages(){
        messages.clear();
        history.clear();
        history.insert(0, "");
    }

    private void setup(){
        fieldlabel.setStyle(new LabelStyle(fieldlabel.getStyle()));
        fieldlabel.getStyle().font = font;
        fieldlabel.setStyle(fieldlabel.getStyle());

        chatfield = new TextField("", new TextField.TextFieldStyle(scene.skin.get(TextField.TextFieldStyle.class)));
        chatfield.setFilter((field, c) -> field.getText().length() < maxTextLength);
        chatfield.getStyle().background = null;
        chatfield.getStyle().font = scene.skin.getFont("chat");
        chatfield.getStyle().fontColor = Color.WHITE;
        chatfield.setStyle(chatfield.getStyle());

        bottom().left().marginBottom(offsety).marginLeft(offsetx * 2).add(fieldlabel).padBottom(6f);

        add(chatfield).padBottom(offsety).padLeft(offsetx).growX().padRight(offsetx).height(28);
    }

    @Override
    public void draw(){
        float opacity = Core.settings.getInt("chatopacity", 100) / 100f;
        float textWidth = Math.min(Core.graphics.getWidth()/1.5f, Unit.dp.scl(700f));

        Draw.color(shadowColor);

        if(chatOpen){
            Fill.crect(offsetx, chatfield.getY(), chatfield.getWidth() + 15f, chatfield.getHeight() - 1);
        }

        super.draw();

        float spacing = chatspace;

        chatfield.visible(chatOpen);
        fieldlabel.visible(chatOpen);

        Draw.color(shadowColor);
        Draw.alpha(shadowColor.a * opacity);

        float theight = offsety + spacing + getMarginBottom();
        for(int i = scrollPos; i < messages.size && i < messagesShown + scrollPos && (i < fadetime || chatOpen); i++){

            layout.setText(font, messages.get(i).formattedMessage, Color.WHITE, textWidth, Align.bottomLeft, true);
            theight += layout.height + textspacing;
            if(i - scrollPos == 0) theight -= textspacing + 1;

            font.getCache().clear();
            font.getCache().addText(messages.get(i).formattedMessage, fontoffsetx + offsetx, offsety + theight, textWidth, Align.bottomLeft, true);

            if(!chatOpen && fadetime - i < 1f && fadetime - i >= 0f){
                font.getCache().setAlphas((fadetime - i) * opacity);
                Draw.color(0, 0, 0, shadowColor.a * (fadetime - i) * opacity);
            }else{
                font.getCache().setAlphas(opacity);
            }

            Fill.crect(offsetx, theight - layout.height - 2, textWidth + Unit.dp.scl(4f), layout.height + textspacing);
            Draw.color(shadowColor);
            Draw.alpha(opacity * shadowColor.a);

            font.getCache().draw();
        }

        Draw.color();

        if(fadetime > 0 && !chatOpen)
            fadetime -= Time.delta() / 180f;
    }

    private void sendMessage(){
        String msg = chatfield.getText();
        clearChatInput();

        if(msg.replaceAll(" ", "").isEmpty()) return;

        history.insert(1, msg);

        new Actions.Chat(){{
            message = msg;
        }}.act();
    }

    public void toggle(){

        if(!chatOpen){
            scene.setKeyboardFocus(chatfield);
            chatOpen = !chatOpen;
            chatfield.fireClick();
        }else{
            scene.setKeyboardFocus(null);
            chatOpen = !chatOpen;
            scrollPos = 0;
            sendMessage();
        }
    }

    public void hide(){
        scene.setKeyboardFocus(null);
        chatOpen = false;
        clearChatInput();
    }

    public void updateChat(){
        chatfield.setText(history.get(historyPos));
        chatfield.setCursorPosition(chatfield.getText().length());
    }

    public void clearChatInput(){
        historyPos = 0;
        history.set(0, "");
        chatfield.setText("");
    }

    public boolean chatOpen(){
        return chatOpen;
    }

    public int getMessagesSize(){
        return messages.size;
    }

    public void addMessage(String message, String sender){
        messages.insert(0, new ChatMessage(message, sender));

        fadetime += 1f;
        fadetime = Math.min(fadetime, messagesShown) + 1f;
    }

    private static class ChatMessage{
        public final String sender;
        public final String message;
        public final String formattedMessage;

        public ChatMessage(String message, String sender){
            this.message = message;
            this.sender = sender;
            if(sender == null){ //no sender, this is a server message?
                formattedMessage = message;
            }else{
                formattedMessage = "[orange][[" + sender + "[orange]]:[WHITE] " + message;
            }
        }
    }

}