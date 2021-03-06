package empire.gfx.ui;

import empire.game.Actions.*;
import empire.game.*;
import empire.game.DemandCard.Demand;
import empire.game.World.City;
import empire.game.World.Tile;
import empire.io.SaveIO;
import io.anuke.arc.Core;
import io.anuke.arc.collection.Array;
import io.anuke.arc.graphics.Color;
import io.anuke.arc.math.Mathf;
import io.anuke.arc.scene.Group;
import io.anuke.arc.scene.event.Touchable;
import io.anuke.arc.scene.ui.Label;
import io.anuke.arc.scene.ui.layout.Table;
import io.anuke.arc.scene.ui.layout.WidgetGroup;
import io.anuke.arc.util.Align;
import io.anuke.arc.util.Strings;
import io.anuke.arc.util.Structs;
import io.anuke.arc.util.Time;

import static empire.gfx.EmpireCore.*;

public class HudFragment{
    private Runnable refresh = () -> {};

    /** Refreshes the city HUD.*/
    public void refresh(){
        refresh.run();
    }

    public void build(Group group){
        WidgetGroup controller = new WidgetGroup();
        controller.visible(() -> net.active());
        controller.touchable(Touchable.childrenOnly);
        controller.setFillParent(true);
        group.addChild(controller);

        //this one is special; it displays other player's start location text
        group.fill(main -> {
            main.top();
            main.visible(() -> !state.player().local && !state.player().chosenLocation);
            main.add("").update(l -> {
                l.setText(state.player().name + ": choosing start location.");
                l.setColor(state.player().color);
            });
        });

        //for local player
        group.fill(main -> {
            main.touchable(Touchable.disabled);
            main.visible(() -> state.player().local && !state.player().chosenLocation && state.player().ai == null);
            main.add("Choose a start city by clicking on it.");
        });

        //hand over control
        group = controller;

        //display event info
        group.fill(events -> {
            Array<EventCard> cards = new Array<>();

            events.visible(() -> {
                cards.clear();
                state.players.each(p -> cards.addAll(p.eventCards));
                return !cards.isEmpty();
            });
            events.bottom().left();
            events.update(() -> {
                events.clearChildren();
                events.add("[lightgray]Active Events:");
                events.row();
                for(EventCard card : cards){
                    events.add("| " + card.name()).color(Color.CORAL).left();
                    events.row();
                }
            });
        });

        //display player info
        group.fill(main -> {
            float width = 250f;
            Collapser[] arr = {null};
            Collapser actions = new Collapser(t -> {
                t.defaults().width(width).height(50f);
                t.addButton("Upgrade", () -> {
                    if(state.player().loco != Loco.freight){
                        new UpgradeLoco().act();
                    }else{
                        ui.showDialog("Upgrade", b -> {
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
                t.addButton("Dump Cargo...", () -> {
                    ui.showDialog("Dump", b -> {
                        b.cont.defaults().size(180f, 50f);

                        for(String c : state.player().cargo){
                            b.cont.addButton("[yellow]" + Strings.capitalize(c), () -> {
                                new DumpCargo(){{ cargo = c; }}.act();
                                b.hide();
                            });
                        }
                    });
                }).disabled(b -> state.player().cargo.isEmpty());
                t.row();
                t.addButton("Discard Cards", () -> {
                    new DiscardCards().act();
                    arr[0].toggle();
                });
                t.row();
                t.addButton("Save", () -> {
                    SaveIO.save(state, Core.files.external("empire_save.json"));
                }).disabled(b -> net.client());
                t.row();
                t.addButton("Load", () -> {
                    try{
                        SaveIO.load(state, Core.files.external("empire_save.json"));
                    }catch(Exception e){
                        e.printStackTrace();
                        ui.showFade("Failed to load save.");
                    }
                }).disabled(b -> net.client());
            }, true);

            arr[0] = actions;

            main.top().left().table("dialogDim", t -> {
                t.margin(10f).left();
                t.defaults().left();
                t.add("").update(l -> {
                    l.setColor(state.localPlayer().color);
                    l.setText(state.localPlayer().name);
                });
                t.row();
                t.addImage("white").fillX().height(3f).pad(3).update(l -> l.setColor(state.localPlayer().color));
                t.row();
                t.label(() -> "[lime]" + state.localPlayer().loco + "[lightgray] loco");
                t.row();
                t.label(() -> "[coral]" + state.localPlayer().money + "[] ECU " + (state.player().local ?  ("| [lime]" + (state.maxRailSpend - state.localPlayer().moneySpent) + "[] this turn") : ""));
                t.row();
                t.label(() -> state.isPreMovement() ? "[orange]Building Phase" : "[orange]" + (state.localPlayer().loco.speed - state.localPlayer().moved) + "[] moves left");
                t.row();
                t.label(() -> state.localPlayer().cargo.isEmpty() ? "[gray]<Empty>" :
                        "[purple]+ " + state.localPlayer().cargo.toString("\n+ "));
            }).minWidth(width);
            main.row();
            main.addImageTextButton("Demands...", "icon-file", 8*3, () -> {
                ui.showDialog("Demand Cards", d -> {
                    Player player = state.localPlayer();
                    d.cont.left();
                    for(DemandCard card : player.demandCards){
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
            }).fillX().height(50);
            main.row();
            main.addImageTextButton("Action...", "icon-down", 16*2, actions::toggle).disabled(b -> !state.player().local).update(t -> {
                if(!actions.isCollapsed() && !state.player().local){
                    actions.toggle();
                }
            }).fillX().height(50);
            main.row();
            main.add(actions).fillX();
        });

        //end turn button
        group.fill(main -> {
            main.top();
            main.addButton("End Turn", () -> {
                new EndTurn().act();
                control.placeLoc = null;
            }).top().height(45f).width(120f).visible(() -> state.player().local);
        });

        //player's turn text
        group.fill(main -> {
            main.top();
            main.visible(() -> !state.player().local && state.player().chosenLocation);
            main.add("").update(l -> {
                l.setText(state.player().name + "'s Turn");
                l.setColor(state.player().color);
            });
        });

        //display cities
        group.fill(main -> {
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
                Player player = state.localPlayer();

                if(lastPlayer[0] != player || lastPosition[0] != player.position){
                    table.clearChildren();
                    selectedCity[0] = null;
                }

                Tile on = control.tileMouse();
                City city = on == null ? null : state.world.getCity(on);

                if(city == null && player.chosenLocation   ){
                    city = player.position.city;
                    if(city == null){
                        city = state.world.getMajorCity(player.position);
                    }
                }

                if(city != selectedCity[0]){
                    table.clearChildren();
                    table.defaults().left();
                    table.margin(10f);

                    boolean atCity = player.position.city == city || state.world.getMajorCity(player.position) == city;

                    //build UI to display it
                    if(city != null){
                        table.addImage("icon-home").size(12*3).padRight(3);
                        table.add(Strings.capitalize(city.name)).color(Color.CORAL);

                        table.row();
                        table.left();
                        for(String good : city.goods){

                            if(atCity && player.local){
                                if(state.canLoadUnload(player, state.world.tile(city.x, city.y))){
                                    table.addImageTextButton(Strings.capitalize(good), "icon-export", 10*2, () -> {
                                        new LoadCargo(){{
                                            cargo = good;
                                        }}.act();
                                    }).colspan(2).left().fillX().disabled(b -> !player.hasCargoSpace() || !state.player().local ).width(190f).height(45f);
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
                        if(Structs.contains(player.demandCards, card -> Structs.contains(card.demands, d -> d.city == fcity))){
                            table.addImage("white").color(Color.ROYAL).growX().pad(10f).colspan(2);
                            table.row();

                            player.eachGoodByCity(city, d -> {
                                boolean has = player.cargo.contains(d.good);
                                if(has && atCity && player.local){
                                    //make sure player is not event-blocked here
                                    if(state.canLoadUnload(player, state.world.tile(fcity.x, fcity.y))){
                                        table.addImageTextButton(Strings.capitalize(d.good) + "[] for[coral] " + d.cost + "[] ECU",
                                        "icon-project-open", 14 * 2, () -> {
                                            new SellCargo(){{
                                                cargo = d.good;
                                            }}.act();
                                        }).colspan(2).left().fillX().width(190f).height(60f).disabled(b -> !state.player().local);
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
                lastPlayer[0] = player;
                lastPosition[0] = player.position;
            });
        });

        //display hovered over ECU info
        group.addChild(new Label(""){{
            touchable(Touchable.disabled);
            update(() -> {
                setText("");
                Tile tile = control.tileMouse();
                if(tile != null && control.placeLoc != null){
                    int totalCost = 0;
                    int totalTiles = 0;
                    Tile last = control.placeLoc;
                    for(Tile other : control.selectedTiles()){
                        if(other != last
                                && !state.world.sameCity(other, last)
                                && !state.player().hasTrack(other, last)){
                            totalCost += state.getTrackCost(last, other);
                            totalTiles ++;
                        }

                        last = other;
                    }
                    setText(totalCost + "[coral] ECU[]\n[lime]" + totalTiles + "[] rails");
                    setColor(state.canSpendTrack(state.player(), totalCost) ? Color.WHITE : Color.SCARLET);
                }
                pack();
                setPosition(Core.input.mouseX(), Core.input.mouseY(), Align.bottomRight);
            });
        }});

        //placement for tracks
        group.fill(t -> {
            t.bottom();
            t.table("button", f -> {
                f.margin(8f).defaults().size(230f, 50f);
                f.addImageTextButton("Place Track", "icon-plus", 16f*2, () -> {
                    control.placeQueued();
                }).height(75f).update(b -> b.setText("Place Track\n" +
                        (state.canSpendTrack(state.player(), (int)control.queueCost()) ? "[green] " : "[scarlet] ") + control.queueCost() + "[coral] ECU"));
                f.row();
                f.addImageTextButton("Cancel", "icon-trash", 14*2f, () -> {
                    control.getQueued().clear();
                });
            });
            t.visible(() -> state.player().local && !control.getQueued().isEmpty());
        });

        group.fill(t -> t.bottom().right().label(() -> "Turn " + state.turn).color(Color.LIGHT_GRAY));

        //paused, only for AI
        group.fill(t -> {
            t.bottom();
            t.table("button", f -> {
                f.add("[coral]PAUSED").pad(5f);
            });
            t.visible(() -> scheduler.isPaused());
        });

        group.fill(t -> {
            t.table("dialogDim", f -> {
                f.add("Thinking...").pad(5f);
            });
            t.visible(() -> scheduler.isThinking());
        });
    }
}
