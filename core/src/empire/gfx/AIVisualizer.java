package empire.gfx;

import empire.ai.CurrentAI.*;
import empire.game.Player;
import empire.game.World.Tile;
import io.anuke.arc.collection.Array;
import io.anuke.arc.graphics.Color;
import io.anuke.arc.graphics.g2d.*;
import io.anuke.arc.math.*;
import io.anuke.arc.util.*;

import static empire.gfx.EmpireCore.state;

public class AIVisualizer implements AIListener{
    private Array<VisPlan> plans = new Array<>();
    private float displayed = 100;

    public AIVisualizer(){

    }

    public void draw(){
        if(plans.isEmpty()) return;

        int total = Math.min(plans.size - 1, (int)displayed);

        for(int i = total; i >= 0; i--){
            VisPlan plan = plans.get(i);
            Tmp.c1.set(Color.GRAY);
            Tmp.c1.a = 1f - ( i /(float)total);
            plan.color.lerp(Tmp.c1, 0.1f * Time.delta());

            drawPlan(plan, i == 0);
        }
    }

    void drawPlan(VisPlan plan, boolean first){
        if(first){
            Lines.stroke(2f);
            Draw.color(Color.CORAL);
        }else{
            Lines.stroke(1f, plan.color);
        }

        Player player = state.player();
        Tile position = player.position;
        int index = 0;

        for(NextAction action : plan.plan.actions){
            float f = index++ / (float)(plan.plan.actions.size - 1);
            if(first){
                Draw.color(Color.SCARLET, Color.YELLOW, f);
            }

            if(action instanceof LoadAction){
                LoadAction l = (LoadAction)action;
                Tile to = state.world.tile(l.city);
                arrow(position.worldx(), position.worldy(), to.worldx(), to.worldy());
                position = to;
            }else if(action instanceof UnloadAction){
                UnloadAction l = (UnloadAction)action;
                Tile to = state.world.tile(l.city);
                arrow(position.worldx(), position.worldy(), to.worldx(), to.worldy());
                position = to;
            }else if(action instanceof LinkCitiesAction){
                LinkCitiesAction l = (LinkCitiesAction)action;
            }
        }
        Draw.reset();
    }

    void arrow(float x, float y, float x2, float y2){
        Tmp.c1.set(Draw.getColor());
        float o = -1f;

        Draw.colorMul(Tmp.c1, 0.75f);

        float angle = Angles.angle(x, y, x2, y2);
        Tmp.v1.trns(angle + 180f, 4f);

        Lines.line(x, y + o, x2, y2 + o, CapStyle.none, -7f);
        Draw.rect("icon-open", x2 + Tmp.v1.x, y2 + o + Tmp.v1.y, angle - 90f);

        Draw.color(Tmp.c1);

        Lines.line(x, y, x2, y2, CapStyle.none, -7f);
        Draw.rect("icon-open", x2 + Tmp.v1.x, y2 + Tmp.v1.y, angle - 90f);
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
