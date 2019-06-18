package empire.ai;

public class PlanCombinations{
    public static final int[][] all = {
            {1, -1, 2, 3, -2, -3},
            {1, -1, 2, 3, -3, -2},
            {1, 2, -1, -2, 3, -3},
            {1, 2, -2, -1, 3, -3},
            {1, -1, 2, -2, 3, -3}, //the only 'reasonable' combination

            //these require 3 cargo spaces
            {1, 2, 3, -1, -2, -3},
            {1, 2, 3, -1, -3, -2},
            {1, 2, 3, -2, -1, -3},
            {1, 2, 3, -2, -3, -1},
            {1, 2, 3, -3, -1, -2},
            {1, 2, 3, -3, -2, -1},
    };
}
