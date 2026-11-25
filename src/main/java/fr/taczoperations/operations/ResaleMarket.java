package fr.taczoperations.operations;

/** Deterministic daily resale prices. All players receive the same server
 * UTC day in the menu; never derive a quote from the client's local clock. */
public final class ResaleMarket {
    static long day(){return BlackMarket.day();}
    public static int variation(OperationsShop.Offer offer,long day){
        if(offer.action()!=OperationsShop.Action.SELL)return 0;
        // A stable offer title gives identical fluctuations for equivalent
        // relic offers across merchants and after server restarts.
        long seed=day*0x9e3779b97f4a7c15L ^ (long)offer.title().hashCode()*0x632be59bd9b4e019L;
        return new java.util.Random(seed).nextInt(31)-15;
    }
    public static int price(OperationsShop.Offer offer,long day){
        if(offer.action()!=OperationsShop.Action.SELL)return offer.price();
        return (int)Math.clamp((offer.price()*(100L+variation(offer,day))+50)/100,1L,Integer.MAX_VALUE);
    }
    private ResaleMarket(){}
}
