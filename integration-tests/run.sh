#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

case "$(docker info --format '{{.Architecture}}')" in
    amd64|x86_64) architecture=x86_64 ;;
    arm64|aarch64) architecture=aarch64 ;;
    *) echo "Unsupported Docker architecture" >&2; exit 1 ;;
esac

docker build --build-arg "EMULATOR_ARCH=$architecture" \
    -t aws-request-signing-emulator integration-tests

runtime_dir=$(mktemp -d "${TMPDIR:-/tmp}/aws-signing-emulator.XXXXXX")
container=
cleanup() {
    status=$?
    if [[ -n "$container" ]]; then
        if [[ "$status" -ne 0 ]]; then
            docker logs "$container" >&2 || true
        fi
        docker stop "$container" >/dev/null || true
    fi
    rm -f "$runtime_dir/emulator_credentials" "$runtime_dir/ca.pem"
    rmdir "$runtime_dir"
    exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

container=$(docker run --detach --rm --memory=2g --memory-swap=2g \
    -p 127.0.0.1::9297 aws-request-signing-emulator)
endpoint="https://$(docker port "$container" 9297/tcp)"

ready=false
for ((attempt=0; attempt<60; attempt++)); do
    if [[ "$(docker inspect --format '{{.State.Running}}' "$container")" != true ]]; then
        break
    fi
    if docker exec "$container" test -f /home/emulator/.aws/emulator/9297/config; then
        docker cp "$container:/home/emulator/.aws/emulator/9297/emulator_credentials" \
            "$runtime_dir/emulator_credentials"
        docker cp "$container:/home/emulator/.aws/emulator/9297/ca.pem" "$runtime_dir/ca.pem"
        if curl --silent --fail --max-time 2 --cacert "$runtime_dir/ca.pem" \
            "$endpoint/__admin/health" >/dev/null; then
            ready=true
            break
        fi
    fi
    sleep 1
done

if [[ "$ready" != true ]]; then
    echo "AWS Emulator did not become ready" >&2
    exit 1
fi

mvn --batch-mode -Pintegration-tests verify \
    "-Daws.emulator.endpoint=$endpoint" "-Daws.emulator.directory=$runtime_dir" "$@"
