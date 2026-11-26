package fr.taczoperations.campaign;

import java.util.Objects;

/** Exact integer loyalty or reputation (thousandths) requirements. */
public record TraderRequirement(String trader, Kind kind, Operator operator, int value) {
    public enum Kind { LEVEL, REPUTATION }
    public enum Operator { GE, LE, LT, GT, EQ }
    public TraderRequirement {
        Objects.requireNonNull(kind);Objects.requireNonNull(operator);
        CampaignTrader.byId(trader);
        if(kind==Kind.LEVEL&&(value<0||value>4))throw new IllegalArgumentException("Invalid loyalty level");
    }
    public boolean matches(int loyalty,int reputationMilli){
        int actual=kind==Kind.LEVEL?loyalty:reputationMilli;
        return switch(operator){
            case GE->actual>=value;case LE->actual<=value;case LT->actual<value;
            case GT->actual>value;case EQ->actual==value;
        };
    }
}
