package empire.gfx.ui;

import io.anuke.arc.function.Consumer;
import io.anuke.arc.graphics.g2d.Draw;
import io.anuke.arc.scene.Action;
import io.anuke.arc.scene.event.Touchable;
import io.anuke.arc.scene.ui.layout.Table;
import io.anuke.arc.scene.ui.layout.WidgetGroup;
import io.anuke.arc.util.ArcRuntimeException;


public class Collapser extends WidgetGroup{
    private Table table;
    private CollapseAction collapseAction = new CollapseAction();
    private boolean collapsed;
    private boolean actionRunning;
    private float currentHeight;

    public Collapser(Consumer<Table> cons, boolean collapsed){
        this.table = new Table();
        this.collapsed = collapsed;
        cons.accept(table);

        updateTouchable();
        addChild(table);
    }

    public void toggle(){
        setCollapsed(!isCollapsed());
    }

    public void setCollapsed(boolean collapse, boolean withAnimation){
        this.collapsed = collapse;
        updateTouchable();

        if(table == null) return;

        actionRunning = true;

        if(withAnimation){
            addAction(collapseAction);
        }else{
            if(collapse){
                currentHeight = 0;
                collapsed = true;
            }else{
                currentHeight = table.getPrefHeight();
                collapsed = false;
            }

            actionRunning = false;
            invalidateHierarchy();
        }
    }

    public void setCollapsed(boolean collapse){
        setCollapsed(collapse, true);
    }

    public boolean isCollapsed(){
        return collapsed;
    }

    private void updateTouchable(){
        touchable(collapsed ? Touchable.disabled : Touchable.enabled);
    }

    @Override
    public void draw(){
        if(currentHeight > 1){
            Draw.flush();
            if(clipBegin(getX(), getY(), getWidth(), currentHeight)){
                super.draw();
                Draw.flush();
                clipEnd();
            }
        }
    }

    @Override
    public void layout(){
        if(table == null) return;

        table.setBounds(0, 0, table.getPrefWidth(), table.getPrefHeight());

        if(!actionRunning){
            if(collapsed)
                currentHeight = 0;
            else
                currentHeight = table.getPrefHeight();
        }
    }

    @Override
    public float getPrefWidth(){
        return table == null ? 0 : table.getPrefWidth();
    }

    @Override
    public float getPrefHeight(){
        if(table == null) return 0;

        if(!actionRunning){
            if(collapsed)
                return 0;
            else
                return table.getPrefHeight();
        }

        return currentHeight;
    }

    public void setTable(Table table){
        this.table = table;
        clearChildren();
        addChild(table);
    }

    @Override
    protected void childrenChanged(){
        super.childrenChanged();
        if(getChildren().size > 1) throw new ArcRuntimeException("Only one actor can be added to CollapsibleWidget");
    }

    private class CollapseAction extends Action{
        @Override
        public boolean act(float delta){
            if(collapsed){
                currentHeight -= delta * 1000;
                if(currentHeight <= 0){
                    currentHeight = 0;
                    actionRunning = false;
                }
            }else{
                currentHeight += delta * 1000;
                if(currentHeight > table.getPrefHeight()){
                    currentHeight = table.getPrefHeight();
                    actionRunning = false;
                }
            }

            invalidateHierarchy();
            return !actionRunning;
        }
    }
}