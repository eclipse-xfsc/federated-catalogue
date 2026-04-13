#!/usr/bin/env python3
"""
Extract class hierarchy from a Gaia-X OWL ontology TTL file.

WHY THIS EXISTS
---------------
The official Gaia-X OWL files (e.g. https://registry.lab.gaia-x.eu/development/owl/2511)
contain thousands of enum individuals whose IRIs embed a '#' in the fragment component
(e.g. <https://w3id.org/gaia-x/2511#CountryNameAlpha2#SM>).  These are not valid IRIs
per RFC 3987, so Apache Jena emits one WARN log line per occurrence.  With ~7,000 such
individuals the test suite either appears to hang or buries real errors in noise.

The catalogueʼs SchemaStoreImpl only needs the class hierarchy (owl:Class declarations
and rdfs:subClassOf relationships) for type resolution via ClaimValidator.getSubjectType.
Everything else — data properties, annotation properties, and especially enum individuals
— is irrelevant and harmful.

This script extracts only what is needed and writes a minimal TTL that:
  • declares an owl:Ontology IRI so SchemaStoreImpl recognises it as an ontology schema
  • contains all owl:Class declarations
  • contains all direct rdfs:subClassOf gx:<ClassName> relationships (blank-node OWL
    restrictions are stripped)

WHAT IS INTENTIONALLY OMITTED
------------------------------
  • owl:Restriction blank nodes — property cardinality/value constraints; not queried
  • owl:equivalentClass — currently absent from the 2511 core hierarchy; if a future
    spec version uses it for Participant / ServiceOffering / Resource, the OWL reasoner
    would infer mutual rdfs:subClassOf and type resolution would silently break without
    those triples present.  Update this script to preserve owl:equivalentClass if that
    happens.
  • owl:intersectionOf / owl:unionOf — same risk as equivalentClass; not present today
  • Data and annotation properties — not queried by ClaimValidator

USAGE
-----
    # Download the latest ontology:
    curl -o /tmp/gx-full.ttl https://registry.lab.gaia-x.eu/development/owl/2511

    # Extract the class hierarchy:
    python3 fc-tools/extract-ontology-hierarchy.py \\
        --input  /tmp/gx-full.ttl \\
        --output fc-service-core/src/main/resources/defaultschema/ontology/gx-2511.ttl \\
        --ontology-iri https://w3id.org/gaia-x/2511 \\
        --source-url https://registry.lab.gaia-x.eu/development/owl/2511

HOW IT WORKS
------------
1. Splits the TTL into blocks on double-newline boundaries (Turtle convention).
2. For each block that declares a gx: owl:Class subject, extracts the subject name.
3. Finds rdfs:subClassOf objects that are direct gx: class references (not blank nodes):
   - Removes content inside [ ... ] blank-node blocks by tracking bracket depth.
   - Collects gx:<Name> tokens that follow 'rdfs:subClassOf' or ',' at depth 0.
4. Writes a minimal TTL with the @prefix declarations, owl:Ontology triple, and one
   triple per class (with rdfs:subClassOf if applicable).
"""

import argparse
import re
import sys
from datetime import datetime, timezone


def extract_direct_superclasses(block: str) -> list[str]:
    """Return list of direct gx: superclass names from a class block."""
    parents: list[str] = []

    for m in re.finditer(r'rdfs:subClassOf', block):
        pos = m.end()
        depth = 0
        token = ''
        i = pos

        while i < len(block):
            ch = block[i]

            if ch == '[':
                depth += 1
                i += 1
                continue
            elif ch == ']':
                depth -= 1
                i += 1
                continue
            elif depth > 0:
                i += 1
                continue

            # Depth 0: end of object list for this predicate
            if ch in (';', '.'):
                break

            if re.match(r'[\w:#]', ch):
                token += ch
            else:
                if token and token.startswith('gx:'):
                    parents.append(token)
                token = ''
            i += 1

        if token and token.startswith('gx:'):
            parents.append(token)

    return parents


def extract(input_path: str, output_path: str, ontology_iri: str, source_url: str) -> None:
    with open(input_path, 'r', encoding='utf-8') as f:
        content = f.read()

    blocks = re.split(r'\n\n+', content)

    classes: set[str] = set()
    subclass_rels: dict[str, list[str]] = {}

    for block in blocks:
        block = block.strip()
        if not block:
            continue

        m = re.match(r'^(gx:\w+)\s+a\s+owl:Class\b', block)
        if not m:
            continue

        subject = m.group(1)
        classes.add(subject)

        parents = extract_direct_superclasses(block)
        if parents:
            subclass_rels[subject] = parents

    now = datetime.now(timezone.utc).strftime('%Y-%m-%d')
    header = f"""\
# Gaia-X 2511 Ontology — class hierarchy only
# Generated {now} from {source_url}
# Stripped to owl:Class declarations and rdfs:subClassOf relationships only.
# Enum individuals and data/annotation properties removed to avoid Jena IRI parse warnings.
#
# WHAT IS OMITTED (intentionally):
#   owl:Restriction blank nodes — property constraints; not used by ClaimValidator
#   owl:equivalentClass / owl:intersectionOf / owl:unionOf — absent from 2511 core
#     hierarchy today.  If a future spec version uses these for Participant /
#     ServiceOffering / Resource, type resolution will silently break.  Regenerate
#     and update fc-tools/extract-ontology-hierarchy.py if that happens.
#   Data and annotation properties — not queried
#
# To regenerate: python3 fc-tools/extract-ontology-hierarchy.py --input <full-owl.ttl>
#                  --output <this-file> --ontology-iri {ontology_iri} --source-url {source_url}

@prefix gx: <https://w3id.org/gaia-x/2511#> .
@prefix owl: <http://www.w3.org/2002/07/owl#> .
@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

<{ontology_iri}> a owl:Ontology .

"""
    lines = [header]

    for cls in sorted(classes):
        parents = subclass_rels.get(cls, [])
        if parents:
            parent_list = " ,\n        ".join(parents)
            lines.append(f"{cls} a owl:Class ;")
            lines.append(f"    rdfs:subClassOf {parent_list} .")
        else:
            lines.append(f"{cls} a owl:Class .")
        lines.append("")

    output = "\n".join(lines[1:])  # class triples joined by newline
    output = lines[0] + output     # header already contains its own newlines

    with open(output_path, 'w', encoding='utf-8') as f:
        f.write(output)

    print(f"Written {len(classes)} classes, {len(subclass_rels)} with subClassOf → {output_path}",
          file=sys.stderr)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--input', required=True, help='Path to the full OWL TTL file')
    parser.add_argument('--output', required=True, help='Path to write the minimal hierarchy TTL')
    parser.add_argument('--ontology-iri', default='https://w3id.org/gaia-x/2511',
                        help='IRI for the owl:Ontology declaration (default: https://w3id.org/gaia-x/2511)')
    parser.add_argument('--source-url', default='https://registry.lab.gaia-x.eu/development/owl/2511',
                        help='Source URL to embed in file header comment')
    args = parser.parse_args()

    extract(args.input, args.output, args.ontology_iri, args.source_url)


if __name__ == '__main__':
    main()
