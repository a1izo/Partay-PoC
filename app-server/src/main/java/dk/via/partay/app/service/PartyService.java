package dk.via.partay.app.service;

import dk.via.partay.app.error.BadRequestException;
import dk.via.partay.app.error.ConflictException;
import dk.via.partay.app.error.ForbiddenException;
import dk.via.partay.app.error.NotFoundException;
import dk.via.partay.app.grpc.DataClient;
import dk.via.partay.app.model.GuestView;
import dk.via.partay.app.model.PartyView;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/** Party and guest rules. */
@Service
public class PartyService {

    static final int MAX_JOIN_CODE_ATTEMPTS = 5;
    private static final Pattern NICKNAME = Pattern.compile("^[\\p{L}\\p{N} _-]{1,30}$");

    private final DataClient data;
    private final JoinCodeGenerator codes;

    public PartyService(DataClient data, JoinCodeGenerator codes) {
        this.data = data;
        this.codes = codes;
    }

    public record Member(PartyView party, GuestView guest) {
    }

    public DataClient.CreatedParty createParty(String rawName, String rawHostNickname) {
        String name = trim(rawName);
        if (name.isEmpty() || name.length() > 100) {
            throw new BadRequestException("Party name must be 1 to 100 characters");
        }
        String hostNickname = validNickname(rawHostNickname);
        for (int attempt = 1; ; attempt++) {
            try {
                return data.createParty(codes.next(), name, hostNickname);
            } catch (ConflictException e) {
                // The only unique key a new party can hit is the join code: try another one.
                if (attempt == MAX_JOIN_CODE_ATTEMPTS) {
                    throw new ConflictException("Could not find a free join code, try again");
                }
            }
        }
    }

    public PartyView getParty(String joinCode) {
        String code = normalizeJoinCode(joinCode);
        try {
            return data.getPartyByJoinCode(code);
        } catch (NotFoundException e) {
            throw new NotFoundException("No party with join code " + code);
        }
    }

    public Member join(String joinCode, String rawNickname) {
        String nickname = validNickname(rawNickname);
        PartyView party = getParty(joinCode);
        try {
            return new Member(party, data.createGuest(party.id(), nickname));
        } catch (ConflictException e) {
            throw new ConflictException("Nickname '" + nickname + "' is already taken in party " + party.joinCode());
        }
    }

    /** A guest acts only inside their own party. */
    public Member requireMember(String joinCode, String guestIdHeader) {
        PartyView party = getParty(joinCode);
        String guestId = guestIdHeader == null ? "" : guestIdHeader.trim();
        if (!isUuid(guestId)) {
            throw new ForbiddenException("Guest is not part of this party");
        }
        GuestView guest;
        try {
            guest = data.getGuest(guestId.toLowerCase(Locale.ROOT));
        } catch (NotFoundException e) {
            throw new ForbiddenException("Guest is not part of this party");
        }
        if (guest.partyId() != party.id()) {
            throw new ForbiddenException("Guest is not part of this party");
        }
        return new Member(party, guest);
    }

    static String normalizeJoinCode(String joinCode) {
        return trim(joinCode).toUpperCase(Locale.ROOT);
    }

    static boolean isUuid(String value) {
        if (value == null || value.length() != 36) {
            return false;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String validNickname(String raw) {
        String nickname = trim(raw);
        if (!NICKNAME.matcher(nickname).matches()) {
            throw new BadRequestException("Nickname must be 1 to 30 characters: letters, digits, space, _ or -");
        }
        return nickname;
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
