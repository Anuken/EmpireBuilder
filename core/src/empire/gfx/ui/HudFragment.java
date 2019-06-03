package empire.gfx.ui;

import empire.game.Actions.*;
import empire.game.*;
import empire.game.DemandCard.Demand;
import empire.game.World.City;
import empire.game.World.Tile;
import io.anuke.arc.Core;
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
        controller.visible(() -> state.localPlayer().chosenLocation);
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
            main.visible(() -> state.player().local && !state.player().chosenLocation);
            main.add("Choose a start city by clicking on it.");
        });

        //hand over control
        group = controller;

        //display event info
        group.fill(events -> {
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
        group.fill(main -> {
            float width = 250f;
            Collapser[] arr = {null};
            Collapser actions = new Collapser(t -> {
                t.defaults().width(width).height(60f);
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
            main.addImageTextButton("Action...", "icon-down", 16*2, actions::toggle)
                    .disabled(b -> !state.player().local).fillX().height(50);
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

                if(city == null){
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
                                    }).colspan(2).left().fillX().disabled(b -> !player.hasCargoSpace()).width(190f).height(45f);
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
                    for(Tile other : control.getTiles(control.placeLoc, tile)){
                        if(other != last &&
                                !(state.world.getMajorCity(tile) == state.world.getMajorCity(last)
                                && state.world.getMajorCity(tile) != null)){
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
    }
}
