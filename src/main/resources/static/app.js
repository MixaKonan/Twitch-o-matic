let app = new Vue({
    el: '#app',
    data: {
        tabs: "streams",
        streamers: {},
        storage: [],
        streamer: "",
        // Server tab
        quality: ["chunked", "720p60", "720p30", "480p30", "360p30", "160p30", "audio_only"],
        addSubscriptionDialog: false,
        addData: {
            username: "",
            quality: []
        },
        subscriptionAlert: false,
        deleteAlert: false,
        deletingStreamer: null,
        // Server tab
        headers: [
            {text: "id", align: "left", value: "_id"},
            {text: "Title", value: "title"},
            {text: "Date", value: "date"},
            {text: "Genre", value: "game"},
            {text: "Actions", value: "name", sortable: false}
        ],
        streams: [],
        pagination: {
            rowsPerPage: 25,
            descending: true,
            sortBy: "date"
        },
        pages: 0,
        search: "",
        // edit
        editStreamModal: false,
        editStreamForm: {},
        editStreamError: false,
        // delete
        deleteStreamModal: false,
        deleteStreamMedia: false,
        deleteStreamUUID: null,
        // add
        addStreamModal: false,
        addStreamForm: {},
        addStreamFormOptional: {},
        addStreamError: false,
        // validate
        validateStreamModal: false,
        validateStreamForm: {},
        validateStreamError: false,
        // Status tab
        searchStatus: "",
        headersStatus: [
            {text: "uuid", align: "left", value: "uuid"},
            {text: "User", value: "user"},
            {text: "Vod", value: "vodId"},
            {text: "Date", value: "date"},
            {text: "Started by", value: "startedBy"},
            {text: "State", value: "state"},
            {sortable: false}
        ],
        statuses: [],
    },
    filters: {
        date(value) {
            if (!value) return "";
            let [date, time] = value.split(/[T.]/);
            date = date.split('-').reverse().join('.');
            return date + " " + time;
        }
    },
    mounted() {
        this.$http.get('/api/v1/subscriptions').then(response => {
            this.streamers = response.data;
            this.streamer = Object.keys(this.streamers)[0];
        });
        this.getStorage();
        this.getStatuses();

        let u = this.updateStatus;
        let stompClient = Stomp.client("ws://localhost:8080/status");
        stompClient.connect({}, function () {
            stompClient.subscribe('/topic/status', message => {
                us(JSON.parse(message.body));
            });
        });
    },
    watch: {
        streamer() {
            this.getStreams();
        },
        streamers() {
            // if (this.streamers.indexOf(this.streamer) === -1) this.streamer = this.streamers[0];
        }
    },
    methods: {
        updateStatus(element) {
            function getIndex() {
                for (let i = 0; i < app.statuses.length; i += 1) if (app.statuses[i].uuid === element.uuid) return i;
                return -1;
            }

            let index = getIndex();
            let statuses = this.statuses;
            if (index !== -1) statuses[index] = element;
            else statuses.push(element);
            this.statuses = [...statuses];
        },
        // Server tab
        // Server status api
        getStorage() {
            this.$http.get('/api/v1/server/storage').then(response => this.storage = response.data);
        },
        importData() {
            this.$http.get('/api/v1/server/import');
        },
        exportData() {
            this.$http.get('/api/v1/server/export');
        },
        // Subscription api
        getStreamers() {
            this.$http.get('/api/v1/subscriptions').then(response => this.streamers = response.data);
        },
        addSubscription() {
            if (!this.addData.quality || !this.addData.username) this.subscriptionAlert = true;
            else {
                this.$http.put(
                    '/api/v1/subscriptions/' + this.addData.username,
                    this.addData.quality.split(',').map(item => item.trim()).filter(item => item)
                ).then(() => {
                    this.getStreamers();
                    this.getStorage();
                });
                this.closeAddSubscriptionDialog();
            }
        },
        deleteSubscription() {
            this.$http.delete('/api/v1/subscriptions/' + this.deletingStreamer).then(() => {
                this.deleteAlert = false;
                this.getStreamers();
                this.getStorage();
            });
        },
        //dialog
        closeAddSubscriptionDialog() {
            this.addData = {username: "", quality: []};
            this.subscriptionAlert = false;
            this.addSubscriptionDialog = false;
        },

        // Streams tab
        // Get streams and pagination
        getStreams() {
            this.$http.get('/api/v1/database/streams/' + this.streamer).then(streams => {
                this.streams = streams.data;
                this.pagination.totalItems = streams.data.length;
                if (this.pagination.rowsPerPage == null || this.pagination.totalItems == null) this.pages = 0;
                else this.pages = Math.ceil(this.pagination.totalItems / this.pagination.rowsPerPage);
            });
        },
        // Edit
        openEditStreamModal(item) {
            this.editStreamModal = true;
            this.editStreamForm = {
                uuid: item._id,
                title: item.title,
                game: item.game,
                date: item.date
            };
        },
        saveStream() {
            if (!!this.editStreamForm.title && !!this.editStreamForm.date && !!this.editStreamForm.game) {
                this.$http.patch('/api/v1/database/streams/' + this.streamer + '/' + this.editStreamForm.uuid, this.editStreamForm).then(() => {
                    this.getStreams();
                    this.cancel();
                    this.editStreamError = false;
                });
            } else this.editStreamError = true;
        },
        // Delete
        openDeleteStreamModal(uuid) {
            this.deleteStreamModal = true;
            this.deleteStreamMedia = false;
            this.deleteStreamUUID = uuid;
        },
        deleteStream() {
            this.$http.delete('/api/v1/database/streams/' + this.streamer + '/' + this.deleteStreamUUID, {params: {deleteMedia: this.deleteStreamMedia}})
                .then(() => {
                    this.getStreams();
                    this.cancel();
                });
        },
        // Add
        openAddStreamModal() {
            this.addStreamModal = true;
            this.addStreamForm = {
                type: "user",
                value: null
            };
            this.addStreamFormOptional = {
                enableWriteTo: false,
                writeTo: ""
            }
        },
        addStream() {
            if (this.addStreamFormOptional.enableWriteTo && !!this.addStreamFormOptional.writeTo)
                this.addStreamForm.writeTo = this.addStreamFormOptional.writeTo;

            if (!!this.addStreamForm.value) {
                this.$http.put('/api/v1/database/streams', this.addStreamForm).then(() => {
                    this.getStreams();
                    this.cancel();
                    this.addStreamError = false;
                });
            } else this.addStreamError = true;
        },
        // Validate
        openValidateStreamModal(item) {
            this.validateStreamModal = true;
            this.validateStreamForm = {
                type: "vod",
                value: !!item && !!item.vodId ? item.vodId : null,
                uuid: !!item ? item._id || item.uuid : null,
                skipMuted: false
            };
        },
        validateStream() {
            if (!!this.validateStreamForm.value && !!this.validateStreamForm.uuid) {
                this.$http.put('/api/v1/database/streams', this.validateStreamForm).then(() => {
                    this.getStreams();
                    this.cancel();
                    this.validateStreamError = false;
                });
            } else this.validateStreamError = true;
        },

        // Status tab
        getStatuses() {
            this.$http.get('/api/v1/status_list').then(response => {
                this.statuses = response.data;
            });
        },
        stopTask(uuid) {
            this.$http.delete('/api/v1/status_list/' + uuid).then(() => {
            });
        },
        // Cancel
        cancel() {
            this.editStreamModal = false;
            this.deleteStreamModal = false;
            this.addStreamModal = false;
            this.validateStreamModal = false;
        },
        background(value) {
            switch (value) {
                case "COMPLETE":
                    return "green";
                case "INITIALIZE":
                    return "yellow";
                case "RUNNING":
                    return "blue";
                case "ERROR":
                    return "red";
                case "STOPPED":
                    return "black";
            }
        }
    }
});