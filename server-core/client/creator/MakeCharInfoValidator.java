package client.creator;

import client.Character;
import provider.Data;
import provider.DataProviderFactory;
import provider.wz.WZFiles;

public class MakeCharInfoValidator {
    private static final MakeCharInfo charFemale;
    private static final MakeCharInfo charMale;
    private static final MakeCharInfo orientCharFemale;
    private static final MakeCharInfo orientCharMale;
    private static final MakeCharInfo premiumCharFemale;
    private static final MakeCharInfo premiumCharMale;

    static {
        Data data = DataProviderFactory.getDataProvider(WZFiles.ETC).getData("MakeCharInfo.img");
        orientCharFemale = new MakeCharInfo(data.getChildByPath("OrientCharFemale"));
        orientCharMale = new MakeCharInfo(data.getChildByPath("OrientCharMale"));
        premiumCharFemale = new MakeCharInfo(data.getChildByPath("PremiumCharFemale"));
        premiumCharMale = new MakeCharInfo(data.getChildByPath("PremiumCharMale"));

        // The Adventurer page is the only creation page left, and it offers the
        // looks the Noblesse and Legend pages used to be pinned to as well.
        charFemale = new MakeCharInfo(data.getChildByPath("Info/CharFemale"))
                .acceptAll(premiumCharFemale).acceptAll(orientCharFemale);
        charMale = new MakeCharInfo(data.getChildByPath("Info/CharMale"))
                .acceptAll(premiumCharMale).acceptAll(orientCharMale);
    }

    private static MakeCharInfo getMakeCharInfo(Character character) {
        return switch (character.getJob()) {
            case BEGINNER, WARRIOR, MAGICIAN, BOWMAN, THIEF, PIRATE -> character.isMale() ? charMale : charFemale;
            case NOBLESSE -> character.isMale() ? premiumCharMale : premiumCharFemale;
            case LEGEND -> character.isMale() ? orientCharMale : orientCharFemale;
            default -> null;
        };
    }

    public static boolean isNewCharacterValid(Character character) {
        MakeCharInfo makeCharInfo = getMakeCharInfo(character);
        if (makeCharInfo == null) return false;

        // Faces and hairs are no longer read off MakeCharInfo.img: which styles may be created is
        // the server's own decision, kept in two tables. The colour rides in the last digit of the
        // hair id, and is allowed simply because that style+colour pair has art.
        boolean male = character.isMale();
        if (!StyleAvailability.allows_face(character.getFace(), male)) return false;
        if (!StyleAvailability.allows_hair(character.getHair(), male)) return false;
        if (!StyleAvailability.has_hair_art(character.getHair())) return false;

        return makeCharInfo.verifyCharacter(character);
    }
}
