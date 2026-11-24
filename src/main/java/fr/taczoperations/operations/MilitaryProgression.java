package fr.taczoperations.operations;

public final class MilitaryProgression {
    private MilitaryProgression() {}
    private static final String[] GRADES = {"Recrue","Soldat","Soldat de première classe","Caporal","Caporal-chef","Sergent","Sergent-chef","Adjudant","Adjudant-chef","Major","Lieutenant","Capitaine","Commandant","Lieutenant-colonel","Colonel","Général de brigade","Général de division","Général de corps d’armée","Général d’armée","Maréchal","Légende"};
    // Cumulative XP milestones agreed for the 1–200 military progression.
    private static final int[] LEVELS = {1,10,20,30,50,75,100,125,150,175,200};
    private static final int[] XP = {0,2000,8000,18000,40000,80000,150000,250000,400000,600000,850000};
    private static final int OMEGA_XP = 5000;

    public static int threshold(int level) {
        if (level <= 1) return 0;
        if (level > 200) return (int)Math.min(Integer.MAX_VALUE, (long)XP[XP.length-1] + (long)OMEGA_XP*(level-200));
        for (int i=1;i<LEVELS.length;i++) {
            if (level <= LEVELS[i]) {
                int span=LEVELS[i]-LEVELS[i-1];
                long delta=(long)(XP[i]-XP[i-1])*(level-LEVELS[i-1]);
                return XP[i-1]+(int)((delta+span/2)/span);
            }
        }
        return XP[XP.length-1];
    }
    public static int level(int xp) {
        if (xp >= threshold(200)) return 200 + (xp-threshold(200))/OMEGA_XP;
        int level=1;
        while(level<200 && xp>=threshold(level+1)) level++;
        return level;
    }
    public static String label(int xp) { int l=level(xp); return l>200 ? "Oméga "+(l-200) : "Niv. "+l; }
    public static String grade(int xp) { int l=level(xp); return l>200 ? "Oméga" : GRADES[l/10]; }
    public static int reward(int l) { return l==200 ? 25000 : l%25==0 ? 5000 : l%10==0 ? 1500 : l%5==0 ? 750 : 150; }
}
