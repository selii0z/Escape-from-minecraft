package fr.taczoperations.operations;

public final class MilitaryProgression {
    private MilitaryProgression() {}
    private static final String[] GRADES = {"Recrue","Soldat","Soldat de première classe","Caporal","Caporal-chef","Sergent","Sergent-chef","Adjudant","Adjudant-chef","Major","Lieutenant","Capitaine","Commandant","Lieutenant-colonel","Colonel","Général de brigade","Général de division","Général de corps d’armée","Général d’armée","Maréchal","Légende"};
    public static int threshold(int level) {
        if (level <= 200) { long n = Math.max(0, level - 1); return (int)(50*n + 5*n*n); }
        return (int)Math.min(Integer.MAX_VALUE, (long)threshold(200) + 5000L*(level-200));
    }
    public static int level(int xp) {
        if (xp >= threshold(200)) return 200 + (xp-threshold(200))/5000;
        int level=1;
        while(level<200 && xp>=threshold(level+1)) level++;
        return level;
    }
    public static String label(int xp) { int l=level(xp); return l>200 ? "Oméga "+(l-200) : "Niv. "+l; }
    public static String grade(int xp) { int l=level(xp); return l>200 ? "Oméga" : GRADES[l/10]; }
    public static int reward(int l) { return l==200 ? 25000 : l%25==0 ? 5000 : l%10==0 ? 1500 : l%5==0 ? 750 : 150; }
}
