package empire.gfx;

import empire.game.*;
import empire.game.Actions.*;
import empire.game.DemandCard.Demand;
import empire.game.GameEvents.WinEvent;
import empire.game.World.City;
import empire.game.World.Tile;
import empire.gfx.ui.ChatFragment;
import empire.gfx.ui.Collapser;
import empire.gfx.ui.EventDialog;
import empire.net.Net;
import io.anuke.arc.Application.ApplicationType;
import io.anuke.arc.ApplicationListener;
import io.anuke.arc.Core;
import io.anuke.arc.Events;
import io.anuke.arc.collection.Array;
import io.anuke.arc.collection.ObjectSet;
import io.anuke.arc.freetype.FreeTypeFontGenerator;
import io.anuke.arc.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import io.anuke.arc.freetype.FreeTypeFontGenerator.Hinting;
import io.anuke.arc.function.Consumer;
import io.anuke.arc.graphics.Color;
import io.anuke.arc.graphics.Colors;
import io.anuke.arc.graphics.g2d.BitmapFont;
import io.anuke.arc.math.Interpolation;
import io.anuke.arc.math.Mathf;
import io.anuke.arc.scene.Scene;
import io.anuke.arc.scene.Skin;
import io.anuke.arc.scene.actions.Actions;
import io.anuke.arc.scene.event.Touchable;
import io.anuke.arc.scene.ui.Dialog;
import io.anuke.arc.scene.ui.Label;
import io.anuke.arc.scene.ui.TextButton;
import io.anuke.arc.scene.ui.layout.Table;
import io.anuke.arc.scene.ui.layout.Unit;
import io.anuke.arc.util.*;

import static empire.gfx.EmpireCore.*;

/** Handles all overlaid UI for the game. */
public class UI implements ApplicationListener{
    public EventDialog events;
    public ChatFragment chat;

    private Runnable refresh = () -> {};

    public UI(){
        Skin skin = new Skin(Core.atlas);
        generateFonts(skin);
        skin.load(Core.files.internal("ui/uiskin.json"));

        Core.scene = new Scene(skin);
        Core.input.addProcessor(Core.scene);

        Events.on(WinEvent.class, event -> {
            ui.showDialog(event.player.name + " is victorious!", dialog -> {
                dialog.cont.add(event.player.name + " has won the game, as they have\nconnected 7 major cities and gotten " + State.winMoneyAmount + " ECU!");
            });
        });
    }

    /** Generates bitmap fonts based on screen size.*/
    void generateFonts(Skin skin){
        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Core.files.internal("ui/prose.ttf"));
        FreeTypeFontParameter param = new FreeTypeFontParameter();
        param.size = (int)(16 * Math.max(Unit.dp.scl(1f), 0.5f));
        param.gamma = 0f;
        param.hinting = Hinting.None;
        param.borderGamma = 0;
        param.borderWidth = 1f;
        param.spaceX = -1;

        BitmapFont font = generator.generateFont(param);
        font.getData().setScale(2f);
        font.getData().down += 11f;

        skin.add("default", font);
        skin.getFont("default").getData().markupEnabled = true;

        skin.add("chat", generator.generateFont(param));
        skin.getFont("chat").getData().markupEnabled = false;

        font = skin.getFont("chat");

        font.getData().setScale(2f);
        font.getData().down += 11f;
    }

    @Override
    public void init(){
        events = new EventDialog();

        net.setErrorHandler(ex -> {
            net.close();

            if(ex.getMessage() != null && ex.getMessage().contains("Address already in use")){
                showDialog("[scarlet]Error", t -> t.cont.add("[coral]Port " + Net.port + " is already in use.\n[]Stop any other servers on the network."));
            }else{
                showDialog("[scarlet]Error", t -> Strings.parseException(ex, true));
            }
        });

        //display event info
        Core.scene.table(events -> {
            events.visible(() -> !state.player().eventCards.isEmpty());
            events.bottom().left();
            events.update(() -> {
                events.clearChildren();
                events.add("[lightgray]Active Events:");
                events.row();
                for(EventCard card : state.player().eventCards){
                    events.add("| " + card.name()).color(Color.CORAL).left();
                    events.row();
                }
            });
        });

        //display player info
        Core.scene.table(main -> {
            float width = 250f;
            Collapser[] arr = {null};
            Collapser actions = new Collapser(t -> {
                t.defaults().width(width).height(60f);
                t.addButton("Upgrade", () -> {
                    if(state.player().loco != Loco.freight){
                        new UpgradeLoco().act();
                    }else{
                        showDialog("Upgrade", b -> {
                            b.cont.defaults().size(180f, 50f);
                            b.cont.addButton("Fast Freight", () -> {
                                new UpgradeLoco(){{ type = 0; }}.act();
                                b.hide();
                            });
                            b.cont.row();
                            b.cont.addButton("Heavy Freight", () -> {
                                new UpgradeLoco(){{ type = 1; }}.act();
                                b.hide();
                            });
                        });
                    }
                    arr[0].toggle();
                }).disabled(b -> state.player().money < State.locoCost).update(b -> {
                    String baseText = "Upgrade";
                    if(state.player().money < State.locoCost){
                        baseText = "Upgrade\n[scarlet]20 ECU required";
                    }
                    b.setText(baseText);
                });
                t.row();
                t.addButton("Discard Cards", () -> {
                    new DiscardCards().act();
                    arr[0].toggle();
                });
            }, true);

            arr[0] = actions;

            main.top().left().table("dialogDim", t -> {
                t.margin(10f);
                t.defaults().left();
                t.add("").update(l -> {
                    l.setColor(state.player().color);
                    l.setText(state.player().name);
                });
                t.row();
                t.addImage("white").fillX().height(3f).pad(3).update(l -> l.setColor(state.player().color));
                t.row();
                t.label(() -> "[lime]" + state.player().loco + "[lightgray] loco");
                t.row();
                t.label(() -> "[coral]" + state.player().money + "[] ECU | [lime]" + (state.maxRailSpend - state.player().moneySpent) + "[] this turn");
                t.row();
                t.label(() -> state.isPreMovement() ? "[orange]Building Phase" : "[orange]" + (state.player().loco.speed - state.player().moved) + "[] moves left");
                t.row();
                t.label(() -> state.player().cargo.isEmpty() ? "[gray]<Empty>" :
                        "[purple]+ " + state.player().cargo.toString("\n+ "));
            }).minWidth(width);
            main.row();
            main.addImageTextButton("Demands...", "icon-file", 8*3, () -> {
                showDialog("Demand Cards", d -> {
                    d.cont.left();
                    for(DemandCard card : state.player().demandCards){
                        d.cont.left();
                        for(Demand m : card.demands){
                            d.cont.add(Strings.format(
                                "[yellow]{0}[] to[lime] {1} ",
                                Strings.capitalize(m.good), Strings.capitalize(m.city.name))).left();
                            d.cont.add("[coral]"+m.cost+"[] ECU").left();
                            d.cont.row();

                        }
                        d.cont.row();
                        d.cont.addImage("white").height(3).color(Color.ROYAL).growX().pad(10f).colspan(2);
                        d.cont.row();
                    }
                });
            }).disabled(b -> !state.player().local).fillX().height(50);
            main.row();
            main.addImageTextButton("Action...", "icon-down", 16*2, actions::toggle)
                    .disabled(b -> !state.player().local).fillX().height(50);
            main.row();
            main.add(actions).fillX();
        });

        Core.scene.table(main -> {
            main.top();
            main.addButton("End Turn", () -> {
                new EndTurn().act();
                control.placeLoc = null;
            }).top().height(45f).width(120f).visible(() -> state.player().local);
        });

        //display cities
        Core.scene.table(main -> {
            City[] selectedCity = {null};
            Player[] lastPlayer = {null};
            Tile[] lastPosition = {null};

            refresh = () -> lastPlayer[0] = null;

            Table table = new Table("dialogDim");
            table.left();
            main.top().right();
            main.add(table).visible(() -> selectedCity[0] != null);

            //automatically update city that's hovered over
            main.update(() -> {
                if(lastPlayer[0] != state.player() || lastPosition[0] != state.player().position){
                    table.clearChildren();
                    selectedCity[0] = null;
                }

                Tile on = control.tileMouse();
                City city = on == null ? null : state.world.getCity(on);

                if(city == null){
                    city = state.player().position.city;
                    if(city == null){
                        city = state.world.getMajorCity(state.player().position);
                    }
                }

                if(city != selectedCity[0]){
                    table.clearChildren();
                    table.defaults().left();
                    table.margin(10f);

                    boolean atCity = state.player().position.city == city || state.world.getMajorCity(state.player().position) == city;

                    //build UI to display it
                    if(city != null){
                        table.addImage("icon-home").size(12*3).padRight(3);
                        table.add(Strings.capitalize(city.name)).color(Color.CORAL);

                        table.row();
                        table.left();
                        for(String good : city.goods){

                            if(atCity && state.player().local){
                                if(state.canLoadUnload(state.player(), state.world.tile(city.x, city.y))){
                                    table.addImageTextButton(Strings.capitalize(good), "icon-export", 10*2, () -> {
                                        new LoadCargo(){{
                                            cargo = good;
                                        }}.act();
                                    }).colspan(2).left().fillX().disabled(b -> !state.player().hasCargoSpace()).width(190f).height(45f);
                                }else{
                                    table.addImage("icon-trash").size(14*2).padRight(3).right().color(Color.SCARLET);
                                    table.add(Strings.capitalize(good)).color(Color.LIGHT_GRAY);
                                }
                            }else{
                                table.addImage("icon-file").size(8*3).padRight(3).right();
                                table.add(Strings.capitalize(good)).color(Color.LIGHT_GRAY);
                            }
                            table.row();
                        }

                        City fcity = city;
                        if(Structs.contains(state.player().demandCards, card -> Structs.contains(card.demands, d -> d.city == fcity))){
                            table.addImage("white").color(Color.ROYAL).growX().pad(10f).colspan(2);
                            table.row();

                            state.player().eachGoodByCity(city, d -> {
                                boolean has = state.player().cargo.contains(d.good);
                                if(has && atCity && state.player().local){
                                    //make sure player is not event-blocked here
                                    if(state.canLoadUnload(state.player(), state.world.tile(fcity.x, fcity.y))){
                                        table.addImageTextButton(Strings.capitalize(d.good) + "[] for[coral] " + d.cost + "[] ECU",
                                        "icon-project-open", 14 * 2, () -> {
                                            new SellCargo(){{
                                                cargo = d.good;
                                            }}.act();
                                        }).colspan(2).left().fillX().width(190f).height(45f);
                                    }else{
                                        table.addImage("icon-trash").size(14 * 2).padRight(3).color(Color.SCARLET).right();
                                        table.add("[lightgray]" + Strings.capitalize(d.good) + "[] for[coral] " + d.cost + "[] ECU");
                                    }
                                }else{
                                    table.addImage(has ? "icon-project-open" : "icon-minus").size(has ? 14*2 : 8 * 3).padRight(3).right().update(i -> {
                                        if(has){
                                            i.getColor().set(Color.WHITE).lerp(Color.GOLD, Mathf.absin(Time.time(), 4f, 1f));
                                        }
                                    });
                                    table.add("[lightgray]" + Strings.capitalize(d.good) + "[] for[coral] " + d.cost + "[] ECU");
                                }
                                table.row();
                            });
                        }

                        if(atCity){
                            table.row();
                            table.add("Current City").color(Color.LIGHT_GRAY).padTop(10).colspan(2).left();
                        }
                    }
                }

                selectedCity[0] = city;
                lastPlayer[0] = state.player();
                lastPosition[0] = state.player().position;
            });
        });

        //display hovered over ECU info
        Core.scene.add(new Label(""){{
            touchable(Touchable.disabled);
            update(() -> {
                setText("");
                Tile tile = control.tileMouse();
                if(tile != null && control.placeLoc != null){
                    int totalCost = 0;
                    int totalTiles = 0;
                    Tile last = control.placeLoc;
                    for(Tile other : control.getTiles(control.placeLoc, tile)){
                        if(other != last){
                            totalCost += state.getTrackCost(last, other);
                            totalTiles ++;
                        }

                        last = other;
                    }
                    setText(totalCost + "[coral] ECU[]\n[lime]" + totalTiles + "[] rails");
                    setColor(state.canSpendRail(state.player(), totalCost) ? Color.WHITE : Color.SCARLET);
                }
                pack();
                setPosition(Core.input.mouseX(), Core.input.mouseY(), Align.bottomRight);
            });
        }});

        //debug info
        if(debug)
        Core.scene.add(new Label(""){{
            touchable(Touchable.disabled);
            update(() -> {
                setText("");
                Tile tile = control.tileMouse();
                if(tile != null){
                    setText(tile.x + "," + tile.y);
                }
                pack();
                setPosition(Core.input.mouseX(), Core.input.mouseY(), Align.bottomRight);
            });
        }});

        //chat
        Core.scene.add(chat  = new ChatFragment());

        //connect info
        Core.scene.table("dialogDim", t -> {
            City city = Array.with(state.world.cities()).random();
            String[] host = {"206.21.122.239"};

            Connect connect = new Connect();
            connect.name = System.getProperty("user.name");
            connect.color = new Color().rand();
            connect.start = state.world.tile(city.x, city.y);
            t.visible(() -> !EmpireCore.net.active());
            t.touchable(Touchable.enabled);

            t.table("button", c -> {
                c.margin(15f);
                c.left().defaults().left();
                c.add("[coral]Connect").colspan(2);
                c.row();
                c.add("Host: ").padRight(5f);
                c.addField(host[0], name -> host[0] = name).size(230f, 50f);
                c.row();
                c.add("Name: ").padRight(5f);
                c.addField(connect.name, name -> connect.name = name).size(230f, 50f);
                c.row();
                c.add("Color: ").padRight(5f);
                c.table(colors -> {
                    colors.left();
                    colors.table("button", p -> p.addImage("white")
                            .update(i -> i.setColor(connect.color)).size(30f)).size(50f);
                    colors.addButton("Pick...", () -> {
                        Dialog dialog = new Dialog("Colors");
                        ObjectSet<Color> seen = new ObjectSet<>();
                        int i = 0;
                        for(String key : Colors.getColors().orderedKeys()){
                            Color color = Colors.getColors().get(key);
                            if(color == Color.BLACK || color == Color.CLEAR || seen.contains(color)) continue;
                            seen.add(color);
                            dialog.cont.addImageButton("white", 30f, () -> {
                                connect.color.set(color);
                                dialog.hide();
                            }).size(50f).get().getImage().setColor(color);
                            if(++i % 6 == 0){
                                dialog.cont.row();
                            }
                        }

                        dialog.show();
                    }).grow();
                }).height(50f).fillX();

                c.row();

                c.table(buttons -> {
                    buttons.addImageTextButton("Connect", "icon-plus", 16*2, () -> {
                        actions.beginConnect(connect, host[0], () -> {
                            showFade("Connected!");
                        }, e -> {
                            e.printStackTrace();
                            showDialog("[scarlet]Error", re -> re.cont.add(e.getMessage()));
                        });
                    }).growX().height(50f);

                    if(Core.app.getType() != ApplicationType.WebGL){
                        TextButton b = buttons.addImageTextButton("Host", "icon-home", 14 * 2, () -> {
                            Player player = state.players.first();
                            player.local = true;
                            player.name = connect.name;
                            player.color = connect.color;
                            player.position = connect.start;
                            net.host();
                            refreshCity();
                        }).growX().height(50f).get();

                        if(debug){
                            b.fireClick();
                        }
                    }
                }).growX().padTop(20f).height(50f).colspan(2);

            });
        });
    }

    @Override
    public void update(){
        Core.scene.act();
        Core.scene.draw();
        Time.update();
    }

    @Override
    public void resize(int width, int height){
        Core.scene.resize(width, height);
    }

    public void refreshCity(){
        refresh.run();
    }

    public void showFade(String text){
        Label label = new Label(text);
        Core.scene.table(t -> {
            t.add(label);
            t.actions(Actions.fadeOut(20f, Interpolation.swingOut), Actions.remove());
        });
    }

    public void showDialog(String title, Consumer<Dialog> cons){
        Dialog dialog = new Dialog(title,"dialog");
        cons.accept(dialog);
        dialog.buttons.addButton("Close", dialog::hide).size(180f, 50f);
        dialog.show();
    }
}
