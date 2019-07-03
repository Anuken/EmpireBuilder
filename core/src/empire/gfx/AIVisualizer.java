package empire.gfx;

import empire.ai.NextAI.*;
import empire.game.Player;
import empire.game.World.Tile;
import io.anuke.arc.collection.Array;
import io.anuke.arc.graphics.Color;
import io.anuke.arc.graphics.g2d.*;
import io.anuke.arc.math.Mathf;
import io.anuke.arc.util.*;

import static empire.gfx.EmpireCore.state;

public class AIVisualizer implements AIListener{
    private Array<VisPlan> plans = new Array<>();
    private float displayed = 10000;

    public AIVisualizer(){

    }

    public void draw(){
        if(plans.isEmpty()) return;

        int total = Math.min(plans.size - 1, (int)displayed);

        for(int i = total; i >= 0; i--){
            VisPlan plan = plans.get(i);
            Tmp.c1.fromHsv(i / (float)total * 360f, 1f, 1f);
            Tmp.c1.a = 1f - ( i /(float)total);
            plan.color.lerp(Tmp.c1, 0.1f * Time.delta());

            if(i == 0){
                Lines.stroke(4f);
                Draw.color(plan.color, Color.WHITE, Mathf.absin(Time.time(), 3f, 1f));
            }else{
                Lines.stroke(1f, plan.color);
            }
            drawPlan(plan);
        }
    }

    void drawPlan(VisPlan plan){
        Player player = state.player();
        Tile position = player.position;

        for(NextAction action : plan.plan.actions){
            if(action instanceof LoadAction){
                LoadAction l = (LoadAction)action;
                Tile to = state.world.tile(l.city);
                Lines.line(position.worldx(), position.worldy(), to.worldx(), to.worldy());
                position = to;
            }else if(action instanceof UnloadAction){
                UnloadAction l = (UnloadAction)action;
                Tile to = state.world.tile(l.city);
                Lines.line(position.worldx(), position.worldy(), to.worldx(), to.worldy());
                position = to;
            }else if(action instanceof LinkCitiesAction){
                LinkCitiesAction l = (LinkCitiesAction)action;
            }
        }
        Draw.reset();
    }

    @Override
    public void planningBegin(){
        plans.clear();
    }

    @Override
    public void planConsidered(Plan plan, float cost){
        plans.add(new VisPlan(plan, cost));
        plans.sort();
    }

    @Override
    public void planChosen(Plan plan){
        plans.clear();
    }

    class VisPlan implements Comparable<VisPlan>{
        final Plan plan;
        final float cost;

        Color color = new Color(Color.WHITE);

        public VisPlan(Plan plan, float cost){
            this.cost = cost;
            this.plan = plan;
        }

        @Override
        public int compareTo(VisPlan o){
            return Float.compare(cost, o.cost);
        }
    }
}
