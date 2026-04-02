#!/bin/bash
# ResPublika API — Test Suite
# Usage: bash test_api.sh

BASE="http://localhost:8081"
DEPUTE_ID="PA722336"
DEPUTE_VOTES="PA794038"
SCRUTIN_UID="VTANR5L17V4618"
PASS=0
FAIL=0
ERRORS=""

test_endpoint() {
    local name="$1"
    local url="$2"
    local jq_check="$3"  # jq expression that must return truthy value

    response=$(curl -s -w "\n%{http_code}" "$url" 2>/dev/null)
    http_code=$(echo "$response" | tail -1)
    body=$(echo "$response" | sed '$d')

    if [[ "$http_code" -ge 200 && "$http_code" -lt 300 ]]; then
        if [[ -n "$jq_check" ]]; then
            val=$(echo "$body" | jq -r "$jq_check" 2>/dev/null)
            if [[ -n "$val" && "$val" != "null" && "$val" != "false" && "$val" != "0" ]]; then
                echo "  ✅ $name (HTTP $http_code) → $jq_check = $val"
                PASS=$((PASS + 1))
            else
                echo "  ⚠️  $name (HTTP $http_code) — CHECK FAILED: $jq_check = $val"
                FAIL=$((FAIL + 1))
                ERRORS="$ERRORS\n  ⚠️  $name → $jq_check = '$val'\n      $(echo "$body" | head -c 200)"
            fi
        else
            echo "  ✅ $name (HTTP $http_code)"
            PASS=$((PASS + 1))
        fi
    else
        echo "  ❌ $name (HTTP $http_code)"
        FAIL=$((FAIL + 1))
        ERRORS="$ERRORS\n  ❌ $name → HTTP $http_code\n      $(echo "$body" | head -c 300)"
    fi
}

test_status() {
    local name="$1"
    local url="$2"
    local expected="$3"

    http_code=$(curl -s -o /dev/null -w "%{http_code}" "$url" 2>/dev/null)
    if [[ "$http_code" == "$expected" ]]; then
        echo "  ✅ $name (HTTP $expected expected)"
        PASS=$((PASS + 1))
    else
        echo "  ❌ $name — expected $expected got $http_code"
        FAIL=$((FAIL + 1))
        ERRORS="$ERRORS\n  ❌ $name → expected $expected, got $http_code"
    fi
}

echo "╔══════════════════════════════════════════════════════╗"
echo "║         🧪 ResPublika API Test Suite                ║"
echo "╚══════════════════════════════════════════════════════╝"
echo ""

# --- Health ---
echo "── Health ──"
test_status "GET /" "$BASE/" "200"

# --- Swagger ---
echo ""
echo "── Swagger ──"
test_status "GET /swagger" "$BASE/swagger" "200"

# --- Députés ---
echo ""
echo "── Députés ──"
test_endpoint "GET /api/deputes" \
    "$BASE/api/deputes" \
    ".total"

test_endpoint "GET /api/deputes?nom=David" \
    "$BASE/api/deputes?nom=David" \
    ".total"

test_endpoint "GET /api/deputes?groupe=PO845470" \
    "$BASE/api/deputes?groupe=PO845470" \
    ".total"

test_endpoint "GET /api/deputes?page=2&limit=5" \
    "$BASE/api/deputes?page=2&limit=5" \
    ".deputes | length"

test_endpoint "GET /api/deputes/{id}" \
    "$BASE/api/deputes/$DEPUTE_ID" \
    ".nom"

test_endpoint "GET /api/deputes/{id} — stats_votes" \
    "$BASE/api/deputes/$DEPUTE_ID" \
    ".stats_votes.total"

test_status "GET /api/deputes/FAKE — 404" \
    "$BASE/api/deputes/FAKE_999" "404"

# --- Votes députés ---
echo ""
echo "── Votes Députés ──"
test_endpoint "GET /api/deputes/{id}/votes" \
    "$BASE/api/deputes/$DEPUTE_VOTES/votes" \
    ".total"

test_endpoint "GET /api/deputes/{id}/votes?limit=5" \
    "$BASE/api/deputes/$DEPUTE_VOTES/votes?page=1&limit=5" \
    ".votes | length"

test_status "GET /api/deputes/FAKE/votes — 404" \
    "$BASE/api/deputes/FAKE_999/votes" "404"

# --- Dissidences ---
echo ""
echo "── Dissidences ──"
test_endpoint "GET /api/deputes/{id}/top-dissidences" \
    "$BASE/api/deputes/$DEPUTE_ID/top-dissidences" \
    ".nb_dissidences >= 0"

test_status "GET /api/deputes/FAKE/top-dissidences — 404" \
    "$BASE/api/deputes/FAKE_999/top-dissidences" "404"

# --- Scrutins ---
echo ""
echo "── Scrutins ──"
test_endpoint "GET /api/scrutins/{uid}/full" \
    "$BASE/api/scrutins/$SCRUTIN_UID/full" \
    ".titre"

test_endpoint "GET /api/scrutins/{uid}/full — groupes" \
    "$BASE/api/scrutins/$SCRUTIN_UID/full" \
    ".groupes | length"

test_endpoint "GET /api/scrutins/{uid}/full — synthese" \
    "$BASE/api/scrutins/$SCRUTIN_UID/full" \
    ".groupes[0].synthese"

test_status "GET /api/scrutins/FAKE/full — 404" \
    "$BASE/api/scrutins/FAKE_SCRUTIN/full" "404"

test_endpoint "GET /api/scrutins/recherche?q=motion" \
    "$BASE/api/scrutins/recherche?q=motion" \
    ".total"

# --- Éthique ---
echo ""
echo "── Éthique ──"
test_endpoint "GET /api/deports/latest" \
    "$BASE/api/deports/latest" \
    ".count"

test_endpoint "GET /api/deputes/{id}/deports — badge" \
    "$BASE/api/deputes/$DEPUTE_ID/deports" \
    ".nb_deports >= 0"

test_status "GET /api/deputes/FAKE/deports — 404" \
    "$BASE/api/deputes/FAKE_999/deports" "404"

# --- Report ---
TOTAL=$((PASS + FAIL))
echo ""
echo "╔══════════════════════════════════════════════════════╗"
echo "║                   📊 RAPPORT                        ║"
echo "╠══════════════════════════════════════════════════════╣"
printf "║  Total: %-3d │ ✅ Pass: %-3d │ ❌ Fail: %-3d        ║\n" $TOTAL $PASS $FAIL
echo "╚══════════════════════════════════════════════════════╝"

if [[ $FAIL -gt 0 ]]; then
    echo ""
    echo "📋 DÉTAIL DES ERREURS :"
    echo -e "$ERRORS"
    echo ""
    echo "⚠️  $FAIL test(s) en échec."
else
    echo ""
    echo "🏆 Tous les tests passent."
fi
