package empire.gfx;

import empire.game.DemandCard;
import empire.game.DemandCard.Demand;
import empire.game.Loco;
import empire.game.Player;
import empire.game.World.City;
import empire.game.World.Tile;
import empire.gfx.ui.Collapser;
import io.anuke.arc.ApplicationListener;
import io.anuke.arc.Core;
import io.anuke.arc.freetype.FreeTypeFontGenerator;
import io.anuke.arc.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import io.anuke.arc.freetype.FreeTypeFontGenerator.Hinting;
import io.anuke.arc.function.Consumer;
import io.anuke.arc.graphics.Color;
import io.anuke.arc.graphics.g2d.BitmapFont;
import io.anuke.arc.math.Interpolation;
import io.anuke.arc.math.Mathf;
import io.anuke.arc.scene.Scene;
import io.anuke.arc.scene.Skin;
import io.anuke.arc.scene.actions.Actions;
import io.anuke.arc.scene.ui.Dialog;
import io.anuke.arc.scene.ui.Label;
import io.anuke.arc.scene.ui.layout.Table;
import io.anuke.arc.scene.ui.layout.Unit;
import io.anuke.arc.util.Align;
import io.anuke.arc.util.Strings;
import io.anuke.arc.util.Structs;
import io.anuke.arc.util.Time;

import static empire.gfx.EmpireCore.control;
import static empire.gfx.EmpireCore.state;

/** Handles all overlaid UI for the game. */
public class UI implements ApplicationListener{

    public UI(){
        Skin skin = new Skin(Core.atlas);
        generateFonts(skin);
        skin.load(Core.files.internal("ui/uiskin.json"));

        Core.scene = new Scene(skin);
        Core.input.addProcessor(Core.scene);
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
    }

    @Override
    public void init(){

        //display player info
        Core.scene.table(main -> {
            float width = 250f;
            Collapser[] arr = {null};
            Collapser actions = new Collapser(t -> {
                t.defaults().width(width).height(60f);
                t.addButton("Upgrade", () -> {
                    if(state.player().loco != Loco.freight){
                        showFade("Upgrade Purchased!");
                        state.purchaseLoco(state.player(), Loco.superFreight);
                    }else{
                        showDialog("Upgrade", b -> {
                            b.cont.defaults().size(180f, 50f);
                            b.cont.addButton("Fast Freight", () -> {
                                showFade("Upgrade Purchased!");
                                state.purchaseLoco(state.player(), Loco.fastFreight);
                                b.hide();
                            });
                            b.cont.row();
                            b.cont.addButton("Heavy Freight", () -> {
                                showFade("Upgrade Purchased!");
                                state.purchaseLoco(state.player(), Loco.heavyFreight);
                                b.hide();
                            });
                        });
                    }
                    arr[0].toggle();
                }).disabled(b -> state.player().money < state.locoCost).update(b -> {
                    String baseText = "Upgrade";
                    if(state.player().money < state.locoCost){
                        baseText = "Upgrade\n[scarlet]20 ECU required";
                    }
                    b.setText(baseText);
                });
                t.row();
                t.addButton("Discard Cards", () -> {
                    state.discardCards(state.player());
                    state.nextPlayer();
                    arr[0].toggle();
                });
            }, true);

            arr[0] = actions;

            main.top().left().table("dialogDim", t -> {
                t.margin(10f);
                t.defaults().left();
                t.label(() -> "Player " + (state.currentPlayer + 1)).update(l -> l.setColor(state.player().color));
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
                        "[purple]+ " + state.player().cargo.toString("\n- "));
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
            }).fillX().height(50);
            main.row();
            main.addImageTextButton("Action...", "icon-down", 16*2, actions::toggle).fillX().height(50);
            main.row();
            main.add(actions).fillX();
        });

        Core.scene.table(main -> {
            main.top();
            main.addButton("End Turn", () -> {
                state.nextPlayer();
                control.placeLoc = null;
            }).top().height(45f).width(120f);
        });

        //display cities
        Core.scene.table(main -> {
            City[] selectedCity = {null};
            Player[] lastPlayer = {null};
            Tile[] lastPosition = {null};

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

                            if(atCity){
                                table.addImageTextButton(Strings.capitalize(good), "icon-export", 10*2, () -> {
                                    state.player().addCargo(good);
                                    showFade(Strings.capitalize(good) + " obtained.");
                                }).colspan(2).left().fillX().disabled(b -> !state.player().hasCargoSpace()).width(190f).height(45f);
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
                                if(has && atCity){
                                    table.addImageTextButton(Strings.capitalize(d.good) + "[] for[coral] " + d.cost + "[] ECU",
                                            "icon-project-open", 14*2, () -> {
                                        state.sellGood(state.player(), d.city, d.good);
                                        lastPlayer[0] = null; //trigger a refresh next frame
                                    }).colspan(2).left().fillX().width(190f).height(45f);

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

        Core.scene.add(new Label(""){{
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
